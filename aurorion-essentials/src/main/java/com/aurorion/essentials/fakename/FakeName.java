package com.aurorion.essentials.fakename;

import net.minecraft.network.chat.Component;

/**
 * Um nome falso resolvido: o texto cru digitado no comando (com os codigos "&", o que fica
 * persistido em disco e trafega na rede), o {@link Component} colorido pronto para exibir, e o
 * texto puro (sem formatacao) usado para validar tamanho/colisao e para o {@code /realname}.
 */
public record FakeName(String raw, Component component, String plain) {
    /** Limite generoso, mas alto o bastante para nao render nametag/tab list ilegivel. */
    public static final int MAX_LENGTH = 48;

    public static FakeName parse(String raw) {
        Component component = LegacyColorCodes.parse(raw);
        return new FakeName(raw, component, component.getString());
    }
}
