package com.aurorion.talk.style;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BalloonPaletteTest {
    @Test
    void contrastMatchesTheKnownExtremes() {
        assertEquals(21.0D, BalloonPalette.contrast(0x000000, 0xFFFFFF), 0.01D);
        assertEquals(1.0D, BalloonPalette.contrast(0x3B6BFF, 0x3B6BFF), 0.001D);
    }

    /** O peso por canal e o ponto: azul puro e muito mais escuro que verde puro para o olho. */
    @Test
    void weighsChannelsLikeTheEyeDoes() {
        assertTrue(BalloonPalette.luminance(0x00FF00) > BalloonPalette.luminance(0x0000FF) * 5);
    }

    @Test
    void unreadableTextIsPushedUntilItCanBeRead() {
        int balloon = 0x3B6BFF;
        int text = 0x2B1C8C;

        assertFalse(BalloonPalette.readable(balloon, text));
        int fixed = BalloonPalette.readableText(balloon, text);

        assertNotEquals(text, fixed);
        assertTrue(BalloonPalette.readable(balloon, fixed),
                "corrigir tem que resultar numa combinacao que passa");
    }

    @Test
    void aReadableChoiceIsLeftExactlyAsItIs() {
        int balloon = 0xFFFFFF;
        int text = 0x18181B;

        assertTrue(BalloonPalette.readable(balloon, text));
        assertEquals(text, BalloonPalette.readableText(balloon, text));
    }

    /** A garantia que a paleta fechada dava antes: nenhuma combinacao sai daqui ilegivel. */
    @Test
    void everyPairInTheGridEndsUpReadable() {
        for (int balloon : BalloonPalette.GRID) {
            for (int text : BalloonPalette.GRID) {
                int fixed = BalloonPalette.readableText(balloon, text);
                assertTrue(BalloonPalette.readable(balloon, fixed),
                        () -> "balao " + BalloonPalette.toHex(balloon) + " com texto " + BalloonPalette.toHex(text));
            }
        }
    }

    /**
     * Preto sobre preto e o caso que motivou a paleta fechada.
     *
     * <p>A correcao para na <b>primeira</b> cor que passa, e nao no branco: o objetivo e resgatar a
     * escolha, nao substitui-la. Sobre fundo preto isso da um cinza claro, que e legivel e ainda
     * lembra o que a pessoa pediu.
     */
    @Test
    void blackOnBlackIsRescued() {
        int fixed = BalloonPalette.readableText(0x000000, 0x000000);

        assertTrue(BalloonPalette.readable(0x000000, fixed));
        assertTrue(BalloonPalette.luminance(fixed) > BalloonPalette.luminance(0x000000),
                "sobre fundo escuro o texto clareia");
        assertNotEquals(0xFFFFFF, fixed, "para no primeiro tom legivel, sem ir direto ao extremo");
    }

    @Test
    void hexRoundTripsAndRefusesGarbage() {
        assertEquals(0x3B6BFF, BalloonPalette.parseHex("#3B6BFF"));
        assertEquals(0x3B6BFF, BalloonPalette.parseHex("3b6bff"));
        assertEquals("#3B6BFF", BalloonPalette.toHex(0x3B6BFF));
        assertEquals(-1, BalloonPalette.parseHex("#GGGGGG"));
        assertEquals(-1, BalloonPalette.parseHex("#FFF"));
        assertEquals(-1, BalloonPalette.parseHex(""));
    }
}
