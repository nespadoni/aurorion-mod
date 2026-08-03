package com.aurorion.essentials.privacy;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;

/**
 * Ponto unico de envio das mensagens de sistema que os mixins de privacidade interceptam
 * (entrada/saida e conquistas). Quando escondida, a mensagem so vai para quem tem OP (nivel 2+)
 * em vez do broadcast normal do {@link PlayerList} — os dois mixins que chamam isto redirecionam
 * o broadcast vanilla porque o jogo nao expoe nenhum evento cancelavel para essas mensagens.
 */
public final class PrivacyMessages {
    private static final int OP_PERMISSION_LEVEL = 2;

    private PrivacyMessages() {
    }

    public static void broadcastUnlessHidden(PlayerList playerList, Component message, boolean bypassHiddenChat, boolean hidden) {
        if (!hidden) {
            playerList.broadcastSystemMessage(message, bypassHiddenChat);
            return;
        }

        for (ServerPlayer player : playerList.getPlayers()) {
            if (player.hasPermissions(OP_PERMISSION_LEVEL)) {
                player.sendSystemMessage(message);
            }
        }
    }
}
