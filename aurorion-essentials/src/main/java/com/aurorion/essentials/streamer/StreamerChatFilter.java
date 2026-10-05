package com.aurorion.essentials.streamer;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.util.Set;

/**
 * Filtra somente a rota de mensagens SYSTEM, antes do HUD e da narracao. Chat assinado/disguised
 * nao passa por aqui. Chaves de traducao funcionam em qualquer idioma do Minecraft.
 *
 * <p>Os argumentos de um envelope de fala sao dados do jogador, nunca sinais administrativos:
 * falar "teleportado", ou mandar um Component de morte no corpo da fala, deve continuar visivel.
 * Siblings sao percorridos porque o aviso privado de morte do Essentials tem uma raiz literal,
 * seguida do Component traduzivel da causa e dos botoes de TP/inventario.</p>
 */
public final class StreamerChatFilter {
    private enum Kind { OTHER, CHAT, OPERATION }

    private static final Set<String> CHAT_KEYS = Set.of(
            "chat.type.text",
            "chat.type.announcement",
            "chat.type.emote",
            "chat.type.team.text",
            "chat.type.team.sent",
            "commands.message.display.incoming",
            "commands.message.display.outgoing"
    );

    private StreamerChatFilter() { }

    public static boolean shouldHide(Component message, boolean overlay, StreamerMode mode) {
        // Actionbar e UI de jogo continuam visiveis. Nao ha tratamento de pacotes de chat assinado.
        if (overlay || mode == StreamerMode.OFF || message == null) return false;
        Kind kind = classify(message);
        if (kind == Kind.CHAT) return false;
        return mode == StreamerMode.ALL_SYSTEM || kind == Kind.OPERATION;
    }

    private static Kind classify(Component message) {
        if (message.getContents() instanceof TranslatableContents contents) {
            String key = contents.getKey();
            // Precisa vir antes de commands.*: /msg tambem e uma fala de jogador.
            if (CHAT_KEYS.contains(key)) return Kind.CHAT;
            if (isOperationKey(key)) return Kind.OPERATION;
        }

        // Marcadores exclusivos de avisos de staff (DeathHistoryEvents / Areas StaffAlert).
        // Apenas o inicio do componente e considerado; nunca uma palavra no corpo de uma fala.
        String text = message.getString();
        if (text.startsWith("[admin] ") || text.startsWith("[Áreas] ")) return Kind.OPERATION;

        Kind result = Kind.OTHER;
        for (Component sibling : message.getSiblings()) {
            Kind child = classify(sibling);
            if (child == Kind.CHAT) return Kind.CHAT;
            if (child == Kind.OPERATION) result = Kind.OPERATION;
        }
        return result;
    }

    private static boolean isOperationKey(String key) {
        return key.startsWith("commands.")
                || key.startsWith("command.")
                || key.startsWith("argument.")
                || key.startsWith("death.")
                || key.equals("chat.type.admin")
                || key.startsWith("chat.type.advancement.")
                || key.equals("gameMode.changed")
                || key.startsWith("multiplayer.player.joined")
                || key.equals("multiplayer.player.left")
                || key.equals("aurorion_ethereal.mural.protector.admin_alert");
    }
}
