package com.aurorion.portais.runtime;

import com.aurorion.portais.AurorionPortais;
import com.aurorion.portais.config.TransitConfig;
import com.aurorion.portais.line.TransitLine;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * O quadro de horarios em execucao: um {@link LineClock} por linha, mais os indices para consultar
 * "que linha manda nesta dimensao" sem varrer nada.
 *
 * <p>Tudo aqui e escrito na thread do servidor (reload de datapack e tick) e lido na thread do
 * servidor (viagem, portal, comando). Os campos sao {@code volatile} e trocados por snapshot inteiro
 * em vez de mutados no lugar, entao uma leitura nunca ve um indice pela metade.
 *
 * <p>{@link #anyOpen()} existe para uma pergunta especifica e barata: a checagem de entrada de
 * portal precisa saber se vale a pena deixar o servidor procurar um destino. Recalcular isso a cada
 * consulta seria varrer todas as linhas; e um {@code boolean} atualizado nas transicoes.
 */
public final class TransitClock {
    private static volatile List<LineClock> clocks = List.of();
    private static volatile Map<ResourceKey<Level>, LineClock> byDimension = Map.of();
    private static volatile boolean anyOpen = false;

    private static int ticksSinceCheck = 0;

    private TransitClock() {
    }

    /** Reconstroi o quadro a partir do catalogo. Chamado na carga do mundo e em cada {@code /reload}. */
    public static void rebuild(List<TransitLine> lines) {
        long now = System.currentTimeMillis();

        List<LineClock> rebuilt = new ArrayList<>(lines.size());
        Map<ResourceKey<Level>, LineClock> index = new HashMap<>();

        for (TransitLine line : lines) {
            LineClock clock = new LineClock(line, now);
            rebuilt.add(clock);

            for (ResourceKey<Level> dimension : line.dimensions()) {
                LineClock previous = index.putIfAbsent(dimension, clock);
                if (previous != null) {
                    // Duas linhas para a mesma dimensao seria ambiguo na hora de decidir se o portao
                    // esta aberto. Vale a primeira na ordem do catalogo, e o conflito vai para o log
                    // em vez de virar comportamento imprevisivel no meio do evento.
                    AurorionPortais.LOGGER.warn(
                            "Dimensao '{}' e atendida por mais de uma linha ('{}' e '{}'). Vale a primeira; ajuste o datapack.",
                            dimension.location(), previous.line().id(), line.id());
                }
            }
        }

        clocks = List.copyOf(rebuilt);
        byDimension = Map.copyOf(index);
        refreshAnyOpen();
    }

    /** Zera o quadro ao desligar o servidor — no servidor integrado, uma sessao nao herda a outra. */
    public static void clear() {
        clocks = List.of();
        byDimension = Map.of();
        anyOpen = false;
        ticksSinceCheck = 0;
    }

    /**
     * Avanca o relogio. Chamado em todo tick do servidor, mas so faz trabalho a cada
     * {@code checkIntervalTicks} — nos demais o custo e um incremento e uma comparacao de int.
     */
    public static void tick(MinecraftServer server) {
        List<LineClock> snapshot = clocks;
        if (snapshot.isEmpty()) return;

        if (++ticksSinceCheck < TransitConfig.CHECK_INTERVAL_TICKS.get()) return;
        ticksSinceCheck = 0;

        // Uma leitura de relogio para todas as linhas, e nao uma por linha: alem de mais barato,
        // impede duas linhas do mesmo tick discordarem sobre que horas sao.
        long now = System.currentTimeMillis();
        boolean open = false;

        for (int i = 0; i < snapshot.size(); i++) {
            LineClock clock = snapshot.get(i);
            clock.tick(server, now);
            open |= clock.isOpen();
        }

        anyOpen = open;
    }

    /** Existe alguma janela aberta agora, em qualquer linha? */
    public static boolean anyOpen() {
        return anyOpen;
    }

    public static List<LineClock> all() {
        return clocks;
    }

    public static boolean isEmpty() {
        return clocks.isEmpty();
    }

    @Nullable
    public static LineClock forDimension(ResourceKey<Level> dimension) {
        return byDimension.get(dimension);
    }

    @Nullable
    public static LineClock forLine(ResourceLocation id) {
        List<LineClock> snapshot = clocks;
        for (int i = 0; i < snapshot.size(); i++) {
            if (snapshot.get(i).line().id().equals(id)) {
                return snapshot.get(i);
            }
        }
        return null;
    }

    /** Reavalia o atalho depois de uma abertura/fechamento manual, fora do ritmo do tick. */
    public static void refreshAnyOpen() {
        List<LineClock> snapshot = clocks;
        boolean open = false;

        for (int i = 0; i < snapshot.size(); i++) {
            open |= snapshot.get(i).isOpen();
        }
        anyOpen = open;
    }
}
