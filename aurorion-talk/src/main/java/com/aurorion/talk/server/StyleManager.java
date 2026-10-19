package com.aurorion.talk.server;

import com.aurorion.core.rate.ActionCooldown;
import com.aurorion.talk.AurorionTalk;
import com.aurorion.talk.network.SyncStylesPayload;
import com.aurorion.talk.network.UpdateStylePayload;
import com.aurorion.talk.style.BalloonStyle;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
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
    /** Uma troca de estilo por segundo por pessoa: cada troca vira um pacote para todo o servidor. */
    private static final ActionCooldown STYLE_COOLDOWN = new ActionCooldown(1_000L);

    private StyleManager() {
    }

    /**
     * O canal e {@code optional()}: cliente sem o mod continua entrando. Mandar um payload para quem nao
     * tem o canal lanca no NeoForge — no login, isso derruba a conexao ("invalid player data"), e num
     * broadcast para no meio. Todo envio daqui passa por esta checagem.
     */
    private static void send(ServerPlayer player, CustomPacketPayload payload) {
        if (player.connection.hasChannel(payload.type().id())) PacketDistributor.sendToPlayer(player, payload);
    }

    private static void broadcast(MinecraftServer server, CustomPacketPayload payload) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) send(player, payload);
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
        // A tela so manda ao fechar; mais de uma troca por segundo e cliente modificado.
        if (!STYLE_COOLDOWN.acquire(sender.getUUID(), System.currentTimeMillis())) return;

        setStyle(sender.getServer(), sender.getUUID(), style.normalized());
    }

    /** Aplica (ou limpa, com {@code style == null}) e faz broadcast. */
    public static void setStyle(MinecraftServer server, UUID player, @Nullable BalloonStyle style) {
        if (!PlayerStyleData.get(server).setStyle(player, style)) return;

        broadcast(server, new UpdateStylePayload(player, Optional.ofNullable(style)));
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

        send(player, new SyncStylesPayload(online));

        BalloonStyle own = data.getStyle(player.getUUID());
        if (own != null) {
            broadcast(server, new UpdateStylePayload(player.getUUID(), Optional.of(own)));
        }
    }
}
