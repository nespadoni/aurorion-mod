package com.aurorion.talk.style;

/**
 * Paletas fixas oferecidas na GUI, na ordem em que aparecem.
 *
 * <p>Fixas de proposito: seletor RGB livre deixaria alguem escolher balao preto com texto preto, e
 * no fim todo mundo precisa conseguir ler a fala dos outros. As cores de balao sao claras e as de
 * texto sao escuras justamente para o contraste nunca quebrar.</p>
 */
public final class BalloonPalette {
    /** Tons claros — vao multiplicados na textura do balao. */
    public static final int[] BALLOON_COLORS = {
            0xFFFFFF, // branco
            0xD5D5D5, // cinza claro
            0x9C9C9C, // cinza
            0x3C3C42, // grafite
            0xFFB3B3, // vermelho
            0xFFC79B, // laranja
            0xFFE08A, // dourado
            0xF3F5A0, // amarelo
            0xC3F0A8, // verde claro
            0xB8F0C0, // verde
            0xA8EFE4, // agua
            0xB3D4FF, // azul claro
            0x9BB0F5, // azul
            0xD9BBFF, // roxo
            0xFFB3D9, // rosa
            0xF0C9A8, // areia
    };

    /** Tons escuros (mais branco, para balao grafite). */
    public static final int[] TEXT_COLORS = {
            0x141414, // quase preto
            0x3D3D3D, // grafite
            0x6B6B6B, // cinza
            0xF5F5F5, // branco
            0x5C1414, // vinho
            0x5C3314, // marrom
            0x4D3600, // bronze
            0x44470A, // oliva
            0x1F4214, // musgo
            0x14421F, // verde escuro
            0x0F3D3D, // petroleo
            0x142B5C, // marinho
            0x14205C, // azul escuro
            0x33144D, // roxo escuro
            0x5C1F3D, // vinho rosado
            0x4D3314, // terra
    };

    /** Cor de texto sugerida ao trocar a cor do balao, na mesma ordem de {@link #BALLOON_COLORS}. */
    private static final int[] SUGGESTED = {
            0x141414, 0x141414, 0x141414, 0xF5F5F5,
            0x5C1414, 0x5C3314, 0x4D3600, 0x44470A,
            0x1F4214, 0x14421F, 0x0F3D3D, 0x142B5C,
            0x14205C, 0x33144D, 0x5C1F3D, 0x4D3314,
    };

    private BalloonPalette() {
    }

    public static int suggestedTextColor(int balloonColor) {
        for (int i = 0; i < BALLOON_COLORS.length; i++) {
            if (BALLOON_COLORS[i] == balloonColor) return SUGGESTED[i];
        }
        return 0x141414;
    }

    public static boolean isBalloonColor(int color) {
        return contains(BALLOON_COLORS, color);
    }

    public static boolean isTextColor(int color) {
        return contains(TEXT_COLORS, color);
    }

    private static boolean contains(int[] palette, int color) {
        for (int candidate : palette) {
            if (candidate == color) return true;
        }
        return false;
    }
}
