package com.aurorion.personagem.client;

import com.aurorion.personagem.network.CreationFeedbackPayload;
import com.aurorion.personagem.network.OpenCreationPayload;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

/**
 * O estado da pergunta, do lado de ca.
 *
 * <p>Guardar o pacote — e nao so abrir a tela — e o que faz a pergunta sobreviver a qualquer coisa
 * que roube a tela no meio: um {@code /kill} de outro mod, uma tela de receita, um Esc que algum
 * atalho force. Enquanto o servidor nao responder "aceito", a tela volta sozinha no tick seguinte.
 *
 * <p>Isso e cortesia, nao regra: quem manda e a retencao em espectador no servidor, que continua
 * valendo mesmo para um cliente que apague esta classe inteira.
 */
public final class ClientCreation {
    @Nullable
    private static OpenCreationPayload pending;

    private ClientCreation() {
    }

    public static void open(OpenCreationPayload payload) {
        pending = payload;
        show();
    }

    public static void feedback(CreationFeedbackPayload payload) {
        if (payload.accepted()) {
            pending = null;
            if (Minecraft.getInstance().screen instanceof CreationScreen screen) screen.accepted();
            return;
        }
        if (Minecraft.getInstance().screen instanceof CreationScreen screen) screen.refuse(payload.message());
    }

    public static void tick() {
        if (pending != null && !(Minecraft.getInstance().screen instanceof CreationScreen)) show();
    }

    /** Sair do servidor esquece a pergunta: o proximo login recebe a dele. */
    public static void clear() {
        pending = null;
    }

    private static void show() {
        OpenCreationPayload payload = pending;
        if (payload != null) Minecraft.getInstance().setScreen(new CreationScreen(payload));
    }
}
