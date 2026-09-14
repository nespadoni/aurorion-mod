package com.aurorion.talk.server;

import com.aurorion.talk.AurorionTalk;
import com.aurorion.talk.network.SyncStylesPayload;
import com.aurorion.talk.network.UpdateStylePayload;
import com.aurorion.talk.style.BalloonStyle;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Ponto unico de escrita dos estilos no servidor: valida, persiste e sincroniza.
 */
public final class StyleManager {
    private StyleManager() {
    }

    /**
     * Trata o {@code SetStylePayload} vindo de um jogador.
     *
     * <p>O cliente e quem decide o proprio estilo — nao ha permissao a checar — mas o
     * <em>conteudo</em> nunca e confiavel: um cliente modificado poderia mandar qualquer caminho de
     * textura, entao validamos a forma antes de aceitar.</p>
     *
     * <p>Cor e caso a parte. Qualquer cor e valida, mas a legibilidade da fala e do servidor: o
     * texto e <b>corrigido</b> aqui se nao contrastar com o balao. Recusar seria pior — a tela do
     * cliente ficaria sem resposta, e um cliente modificado continuaria mandando fala ilegivel.</p>
     */
    public static void requestStyle(ServerPlayer sender, BalloonStyle style) {
        if (!style.isWellFormed()) {
            AurorionTalk.LOGGER.warn("Ignorando estilo mal formado de {}: {}", sender.getGameProfile().getName(), style);
            return;
        }

        setStyle(sender.getServer(), sender.getUUID(), style.normalized());
    }

    /** Aplica (ou limpa, com {@code style == null}) e faz broadcast. */
    public static void setStyle(MinecraftServer server, UUID player, @Nullable BalloonStyle style) {
        if (!PlayerStyleData.get(server).setStyle(player, style)) return;

        PacketDistributor.sendToAllPlayers(new UpdateStylePayload(player, Optional.ofNullable(style)));
    }

    @Nullable
    public static BalloonStyle getStyle(MinecraftServer server, UUID player) {
        return PlayerStyleData.get(server).getStyle(player);
    }

    /** Manda para quem acabou de entrar o estado atual, e avisa os demais sobre o recem-chegado. */
    public static void onPlayerJoin(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;

        PlayerStyleData data = PlayerStyleData.get(server);

        Map<UUID, BalloonStyle> online = new HashMap<>();
        for (ServerPlayer other : server.getPlayerList().getPlayers()) {
            BalloonStyle style = data.getStyle(other.getUUID());
            if (style != null) online.put(other.getUUID(), style);
        }

        PacketDistributor.sendToPlayer(player, new SyncStylesPayload(online));

        BalloonStyle own = data.getStyle(player.getUUID());
        if (own != null) {
            PacketDistributor.sendToAllPlayers(new UpdateStylePayload(player.getUUID(), Optional.of(own)));
        }
    }
}
