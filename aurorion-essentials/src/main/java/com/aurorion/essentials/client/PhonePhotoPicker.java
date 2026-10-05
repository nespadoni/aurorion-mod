package com.aurorion.essentials.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Reaproveita o seletor de fotos do DM do Gram (lista a galeria do personagem, com o visual do
 * telefone) para outros destinos. O seletor aberto por aqui fica marcado; o
 * {@code PhonePhotoPickerMixin} desvia o clique numa foto e o botao voltar para o destino marcado.
 */
public final class PhonePhotoPicker {
    /** Para onde vai a foto escolhida. */
    public sealed interface Target permits Messages, GramPost {
        void pick(Path photo);

        Screen back();
    }

    /** Conversa do app Mensagens. */
    public record Messages(String threadId) implements Target {
        @Override
        public void pick(Path photo) {
            PhoneUi.sendMessagePhoto(threadId, photo);
        }

        @Override
        public Screen back() {
            return PhoneUi.newScreen("PhoneMessageChatScreen", new Class<?>[]{String.class}, threadId);
        }
    }

    /** Novo post do Gram: a foto segue para a mesma tela de legenda que a camera abre. */
    public record GramPost() implements Target {
        @Override
        public void pick(Path photo) {
            var player = Minecraft.getInstance().player;
            if (player == null) return;
            PhoneUi.open(PhoneUi.newScreen("PhoneInstagramCaptionScreen",
                    new Class<?>[]{Path.class, String.class}, photo, player.getGameProfile().getName()));
        }

        @Override
        public Screen back() {
            return PhoneUi.newScreen("PhoneInstagramScreen", new Class<?>[0]);
        }
    }

    private static final Map<Screen, Target> OPEN = Collections.synchronizedMap(new WeakHashMap<>());

    private PhonePhotoPicker() {
    }

    public static void open(Target target) {
        String threadId = target instanceof Messages messages ? messages.threadId() : "";
        Screen picker = PhoneUi.newScreen("PhoneInstagramDmPhotoPickerScreen", new Class<?>[]{String.class}, threadId);
        if (picker == null) return;
        OPEN.put(picker, target);
        Minecraft.getInstance().setScreen(picker);
    }

    public static Target targetOf(Object picker) {
        return OPEN.get(picker);
    }
}
