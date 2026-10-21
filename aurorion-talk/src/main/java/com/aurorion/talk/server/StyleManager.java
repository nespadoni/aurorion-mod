package com.aurorion.talk.server;

import com.aurorion.core.rate.ActionCooldown;
import com.aurorion.talk.AurorionTalk;
import com.aurorion.talk.network.SyncStylesPayload;
import com.aurorion.talk.network.UpdateStylePayload;
import com.aurorion.talk.style.BalloonStyle;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Ponto unico de escrita dos estilos no servidor: valida, persiste e sincroniza.
 */
public final class StyleManager {
    /**
     * Uma troca de estilo a cada 2 s por jogador.
     *
     * <p>Cada troca aceita vira um pacote para <b>cada</b> jogador online: sem limite, um cliente
     * modificado mandando {@code SetStylePayload} em laco faria o servidor emitir 80 pacotes por
     * pacote recebido — amplificacao barata. A tela legitima manda um pacote ao fechar, entao 2 s
     * nao atrapalha ninguem que esta so escolhendo cor.
     */
    private static final ActionCooldown CHANGES = new ActionCooldown(2_000L);

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
     *
     * <p>A ordem importa: o estilo igual ao atual sai antes do limite (fechar a tela sem mudar nada
     * nao gasta a vez de ninguem, e nao gera broadcast); o mal formado so e logado quando o limite
     * deixa, senao o mesmo laco que amplificaria rede encheria o log.</p>
     */
    public static void requestStyle(ServerPlayer sender, BalloonStyle style) {
        MinecraftServer server = sender.getServer();
        if (server == null) return;

        UUID account = sender.getUUID();

        if (!style.isWellFormed()) {
            if (CHANGES.acquire(account, Util.getMillis())) {
                AurorionTalk.LOGGER.warn("Ignorando estilo mal formado de {}: {}", sender.getGameProfile().getName(), style);
            }
            return;
        }

        BalloonStyle normalized = style.normalized();
        BalloonStyle current = getStyle(server, account);
        if (Objects.equals(normalized, current)) return;

        if (!CHANGES.acquire(account, Util.getMillis())) {
            // A tela do cliente ja mostra o estilo novo como se tivesse valido; devolver o atual
            // evita que o proprio jogador veja um balao que ninguem mais ve.
            sendIfSupported(sender, new UpdateStylePayload(account, Optional.ofNullable(current)));
            sender.displayClientMessage(Component.literal("Espere um instante antes de trocar o balao de novo.")
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }

        setStyle(server, account, normalized);
    }

    /**
     * Aplica (ou limpa, com {@code style == null}) e faz broadcast. E o unico caminho de escrita —
     * quem grava direto no {@link PlayerStyleData} deixa os clientes mostrando o estilo antigo ate o
     * proximo login.
     */
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

        // Sem o mod, o snapshot nem e montado: o cliente nao teria o que fazer com ele.
        if (hasChannel(player, SyncStylesPayload.TYPE)) {
            Map<UUID, BalloonStyle> online = new HashMap<>();
            for (ServerPlayer other : server.getPlayerList().getPlayers()) {
                BalloonStyle style = data.getStyle(other.getUUID());
                if (style != null) online.put(other.getUUID(), style);
            }

            PacketDistributor.sendToPlayer(player, new SyncStylesPayload(online));
        }

        BalloonStyle own = data.getStyle(player.getUUID());
        if (own != null) {
            broadcast(server, new UpdateStylePayload(player.getUUID(), Optional.of(own)));
        }
    }

    public static void clear() {
        CHANGES.clear();
    }

    /**
     * O canal e opcional ({@code TalkNetwork}): quem entra sem o mod nao o tem, e o NeoForge recusa
     * payload para quem nao negociou o canal. Filtrar aqui, como os outros mods do ecossistema, e o
     * que mantem o cliente limpo conectado.
     */
    private static void broadcast(MinecraftServer server, UpdateStylePayload payload) {
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            sendIfSupported(online, payload);
        }
    }

    private static void sendIfSupported(ServerPlayer player, UpdateStylePayload payload) {
        if (hasChannel(player, UpdateStylePayload.TYPE)) PacketDistributor.sendToPlayer(player, payload);
    }

    private static boolean hasChannel(ServerPlayer player, CustomPacketPayload.Type<?> type) {
        return player.connection != null && player.connection.hasChannel(type);
    }
}
