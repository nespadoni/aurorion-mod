package com.aurorion.portais.runtime;

import com.aurorion.portais.config.TransitConfig;
import com.aurorion.core.text.TimeFormat;
import com.aurorion.portais.line.Schedule;
import com.aurorion.portais.line.Station;
import com.aurorion.portais.line.TransitLine;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * As chamadas de estacao.
 *
 * <p>Distribuicao pensada para 90 pessoas no chat: <b>chat so para o que e raro</b> (algumas
 * chamadas por semana por linha), <b>actionbar para o que se repete</b> (a contagem regressiva do
 * fim da janela) — e a actionbar so vai para quem esta dentro da dimensao, que sao as unicas
 * pessoas para quem ela e uma informacao acionavel. Mesma logica do cleanup do aurorion-essentials
 * (SDD §5.4): aviso que empilha no historico do chat e pior que o evento que ele anuncia.
 */
public final class TransitAnnouncer {
    private TransitAnnouncer() {
    }

    /** "O Expresso Igneo parte em 15min. Embarque na Estacao Central." */
    public static void warn(MinecraftServer server, TransitLine line, int minutesBefore) {
        if (!announces()) return;

        Component message = line.station()
                .map(station -> Component.translatable("aurorion_portais.aviso.chamada.estacao",
                        line.coloredName(), TimeFormat.duration(minutesBefore * 60_000L), station.label()))
                .orElseGet(() -> Component.translatable("aurorion_portais.aviso.chamada",
                        line.coloredName(), TimeFormat.duration(minutesBefore * 60_000L)));

        broadcast(server, message, 1.0F);
    }

    /** Janela aberta: e agora que da para atravessar. */
    public static void boarding(MinecraftServer server, TransitLine line, long closesAt, long now) {
        if (!announces()) return;

        Component message = Component.translatable("aurorion_portais.aviso.embarque",
                line.coloredName(), TimeFormat.duration(closesAt - now));

        broadcast(server, message, 1.2F);
    }

    /** Janela fechada. Quem ficou dentro, ficou. */
    public static void departed(MinecraftServer server, TransitLine line) {
        if (!announces()) return;

        broadcast(server, Component.translatable("aurorion_portais.aviso.partiu", line.coloredName()), 0.8F);
    }

    /**
     * Contagem regressiva na actionbar, so para quem esta dentro das dimensoes da linha.
     *
     * <p>Este e o unico ponto do mod que percorre a lista de jogadores mais de uma vez por semana, e
     * mesmo assim: 1x por segundo, apenas no ultimo minuto de uma janela aberta. Fora disso o custo
     * e exatamente zero — nao ha ticker por jogador em lugar nenhum.
     */
    public static void countdown(MinecraftServer server, TransitLine line, int secondsLeft) {
        Component message = Component.translatable("aurorion_portais.aviso.contagem",
                line.coloredName(), secondsLeft).withStyle(ChatFormatting.GOLD);

        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        for (int i = 0; i < players.size(); i++) {
            ServerPlayer player = players.get(i);
            if (line.serves(player.level().dimension())) {
                player.displayClientMessage(message, true);
            }
        }
    }

    /** Aviso de que a viagem foi barrada, ja com a informacao util: quando abre de novo. */
    public static Component deniedMessage(ResourceKey<Level> dimension, boolean leaving) {
        LineClock clock = TransitClock.forDimension(dimension);
        Component label = dimensionLabel(dimension);

        Component headline = leaving
                ? Component.translatable("aurorion_portais.barrado.saida", label)
                : Component.translatable("aurorion_portais.barrado.entrada", label);

        if (clock == null) {
            return headline.copy().withStyle(ChatFormatting.RED);
        }

        long next = clock.nextDeparture();
        if (next == Schedule.NEVER) {
            return headline.copy().withStyle(ChatFormatting.RED);
        }

        return Component.empty()
                .append(headline)
                .append(" ")
                .append(Component.translatable("aurorion_portais.barrado.proximo",
                        TimeFormat.clockTime(next, clock.line().schedule().zone()),
                        TimeFormat.duration(next - System.currentTimeMillis())))
                .withStyle(ChatFormatting.RED);
    }

    /**
     * Nome apresentavel de uma dimensao: o da linha que a atende, ou o id cru quando nenhuma atende.
     * O id cru e feio de proposito — dimensao trancada e sem linha e um buraco na configuracao, e
     * aparecer feia no chat e o que faz alguem reparar nela.
     */
    public static Component dimensionLabel(ResourceKey<Level> dimension) {
        LineClock clock = TransitClock.forDimension(dimension);
        return clock != null ? clock.line().coloredName() : Component.literal(dimension.location().toString());
    }

    /** Estacao de desembarque declarada para a dimensao, se houver linha e se ela declarou uma. */
    @Nullable
    public static Station arrivalFor(ResourceKey<Level> dimension) {
        LineClock clock = TransitClock.forDimension(dimension);
        return clock == null ? null : clock.line().arrivalIn(dimension).orElse(null);
    }

    private static boolean announces() {
        // Com enforce desligado o relogio continua correndo (para /portais nao mentir), mas anunciar
        // "o portal fechou" enquanto todo mundo atravessa livremente seria pior que nao anunciar.
        return TransitConfig.ANNOUNCE_IN_CHAT.get() && TransitConfig.ENFORCE.get();
    }

    private static void broadcast(MinecraftServer server, Component message, float pitch) {
        server.getPlayerList().broadcastSystemMessage(message, false);

        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        for (int i = 0; i < players.size(); i++) {
            players.get(i).playNotifySound(SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.MASTER, 0.5F, pitch);
        }
    }
}
