package com.aurorion.essentials.voice;

import org.jetbrains.annotations.Nullable;

/**
 * Quando um grupo de voz e "privado" demais para o servidor. Classe pura: recebe o que importa do
 * grupo e da config, e nao conhece o Voice Chat — da para testar sem ele.
 */
public final class VoiceGroupPolicy {
    private VoiceGroupPolicy() {
    }

    /**
     * @param open      o grupo e do tipo Aberto
     * @param password  o grupo tem senha
     * @return o motivo da recusa, para mostrar a quem tentou, ou {@code null} se o grupo pode existir
     */
    @Nullable
    public static String refusal(boolean open, boolean password, boolean onlyOpenGroups, boolean allowPasswords) {
        if (onlyOpenGroups && !open) {
            return "Grupos privados nao sao permitidos neste servidor. Crie o grupo com o tipo Aberto: "
                    + "quem estiver perto de voce continua ouvindo o que voce fala.";
        }
        if (!allowPasswords && password) {
            return "Grupos com senha nao sao permitidos neste servidor. Crie o grupo sem senha.";
        }
        return null;
    }
}
