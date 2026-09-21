package com.aurorion.essentials.privacy;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;

/**
 * Ponto unico de envio das mensagens de sistema que os mixins de privacidade interceptam
 * (entrada/saida, conquista e morte).
 *
 * <p>Os quatro mixins redirecionam o mesmo {@code PlayerList#broadcastSystemMessage} do vanilla
 * porque o jogo <b>nao expoe evento cancelavel</b> para nenhuma dessas mensagens. Eles chegam aqui
 * com a {@link Visibility} da sua config e nada mais: a regra de quem recebe mora num lugar so, e
 * nao repetida em quatro mixins.
 */
public final class PrivacyMessages {
    private static final int OP_PERMISSION_LEVEL = 2;

    private PrivacyMessages() {
    }

    public static void broadcast(PlayerList playerList, Component message, boolean bypassHiddenChat,
                                 Visibility visibility) {
        switch (visibility) {
            case EVERYONE -> playerList.broadcastSystemMessage(message, bypassHiddenChat);
            case ADMINS -> {
                for (ServerPlayer player : playerList.getPlayers()) {
                    if (player.hasPermissions(OP_PERMISSION_LEVEL)) {
                        player.sendSystemMessage(message);
                    }
                }
            }
            case NOBODY -> {
                // Nada a enviar. O log do servidor continua registrando o evento por conta propria.
            }
        }
    }
}
