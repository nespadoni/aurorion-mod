package com.aurorion.portais.line;

import com.aurorion.core.datapack.DatapackRegistry;
import com.aurorion.portais.AurorionPortais;
import com.aurorion.portais.runtime.TransitClock;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;

/**
 * As linhas dos datapacks, recarregadas na carga do mundo e a cada {@code /reload}.
 *
 * <p>O catalogo e autoridade exclusiva do servidor — o cliente nunca ve esses arquivos e nao precisa
 * ver: nada do que este mod faz e renderizado, so mensagem de chat ja resolvida no servidor.
 *
 * <p>A leitura em si e do {@link DatapackRegistry}, no {@code aurorion-core}. O que sobra aqui e o
 * que e do dominio: a ordem das linhas e o gancho que reconstroi o quadro de horarios depois de cada
 * carga — sem ele, um {@code /reload} que mude horario so valeria no proximo boot, e uma linha
 * apagada do datapack continuaria com a janela aberta no estado antigo.
 */
public final class LineCatalog {
    /**
     * Prefixo {@code aurorion/} de proposito, igual ao datapack de casas: num modpack pesado,
     * {@code data/<ns>/linhas/} tem chance real de colidir com outro mod.
     */
    public static final String DIRECTORY = "aurorion/linhas";

    private static final Comparator<TransitLine> ORDER = Comparator
            .comparingInt(TransitLine::order)
            .thenComparing(line -> line.id().toString());

    private static final DatapackRegistry<TransitLine> REGISTRY =
            new DatapackRegistry<>(DIRECTORY, AurorionPortais.LOGGER, TransitLine::codec, ORDER)
                    .onReload(TransitClock::rebuild);

    private LineCatalog() {
    }

    /** Um listener novo para o {@code AddReloadListenerEvent} — o vanilla pede um a cada reload. */
    public static PreparableReloadListener listener() {
        return REGISTRY.listener();
    }

    /** Todas as linhas, na ordem em que devem aparecer em {@code /portais}. Nunca nula. */
    public static List<TransitLine> all() {
        return REGISTRY.all();
    }

    public static boolean isEmpty() {
        return REGISTRY.isEmpty();
    }

    @Nullable
    public static TransitLine byId(ResourceLocation id) {
        return REGISTRY.get(id);
    }

    public static List<ResourceLocation> ids() {
        return REGISTRY.ids();
    }
}
