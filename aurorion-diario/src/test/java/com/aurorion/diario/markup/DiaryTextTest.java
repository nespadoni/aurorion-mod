package com.aurorion.diario.markup;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiaryTextTest {
    private static final String EMOJI = "😀"; // um caractere para o site, duas unidades para o pacote

    @Test
    void clipNeverSplitsASurrogatePair() {
        String title = "a" + EMOJI;
        assertEquals("a", DiaryText.clip(title, 2));
        assertEquals(title, DiaryText.clip(title, 3));
        assertEquals("", DiaryText.clip(null, 5));
    }

    /** O site conta caracteres: 200 emojis sao um titulo valido, e cabem no pacote de 400 unidades. */
    @Test
    void clipCodePointsCountsLikeTheSite() {
        String title = EMOJI.repeat(201);
        String clipped = DiaryText.clipCodePoints(title, 200);
        assertEquals(200, clipped.codePointCount(0, clipped.length()));
        assertEquals(400, clipped.length());
    }

    @Test
    void oversizedMarkupBecomesTheReadOnlyPlaceholder() {
        assertEquals("curto", DiaryText.fitOrPlaceholder("curto", 10));
        assertTrue(DiaryText.isPlaceholder(DiaryText.fitOrPlaceholder("x".repeat(11), 10)));
        // O escape cresce o texto: 30 mil asteriscos do site viram 60 mil caracteres de marcacao.
        String doc = "{\"blocks\":[{\"type\":\"paragraph\",\"runs\":[{\"text\":\"" + "*".repeat(30_000) + "\"}]}]}";
        assertTrue(DiaryText.isPlaceholder(DiaryMarkup.toMarkup(JsonParser.parseString(doc).getAsJsonObject(), 40_000)));
    }

    @Test
    void keysAcceptUuidsAndRefuseLineBreaks() {
        assertTrue(DiaryText.validKey("3f0c2a4e-9b1d-4c55-8a7e-1d2f3a4b5c6d"));
        assertFalse(DiaryText.validKey("abc\nINFO falso"));
        assertFalse(DiaryText.validKey(""));
        assertFalse(DiaryText.validKey("x".repeat(41)));
        assertFalse(DiaryText.validKey(null));
    }

    /** Documento do site com tipos errados nao derruba a conversao. */
    @Test
    void oddDocumentTypesDoNotThrow() {
        String doc = "{\"blocks\":[{\"type\":\"heading\",\"level\":\"tres\",\"runs\":[{\"text\":\"T\",\"marks\":[{\"x\":1},\"bold\"]}]},"
                + "{\"type\":\"image\",\"attachment_id\":\"abc\"}]}";
        String markup = DiaryMarkup.toMarkup(JsonParser.parseString(doc).getAsJsonObject());
        assertEquals("## **T**\n\n[[imagem:0||]]", markup);
    }
}
