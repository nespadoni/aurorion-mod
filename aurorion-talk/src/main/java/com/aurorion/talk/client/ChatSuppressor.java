package com.aurorion.talk.client;

/**
 * Sinaliza para o {@code ChatComponentMixin} que a proxima mensagem a entrar no HUD ja virou balao
 * e deve ser engolida.
 *
 * <p>A flag e armada no ponto de injecao imediatamente anterior ao {@code ChatComponent.addMessage},
 * na mesma thread do cliente, e consumida ali dentro — a janela e de algumas instrucoes, entao nao
 * ha risco de vazar para a mensagem seguinte.</p>
 *
 * <p>Interceptamos no HUD, e nao em {@code ChatListener.showMessageToPlayer}, de proposito:
 * cancelar la faria o cliente devolver {@code markMessageAsProcessed(false)} e bagunçar a cadeia de
 * assinaturas do chat seguro, o que em servidor grande derruba gente.</p>
 */
public final class ChatSuppressor {
    private static boolean armed;

    private ChatSuppressor() {
    }

    public static void arm() {
        armed = true;
    }

    public static boolean consume() {
        boolean was = armed;
        armed = false;
        return was;
    }
}
