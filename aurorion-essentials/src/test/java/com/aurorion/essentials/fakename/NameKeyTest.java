package com.aurorion.essentials.fakename;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A regra que decide quando dois nomes "sao o mesmo". O que estes testes protegem: cada grafia aqui
 * e uma que o olho le igual a "Bella Noob" — se alguma escapasse, era uma forma de vestir o nome de
 * outra pessoa e receber o PIX dela pelo celular.
 */
class NameKeyTest {
    private static final String BELLA = "bella noob";

    @Test
    void coresCaixaEEspacosNaoImportam() {
        assertEquals(BELLA, NameKey.of("&6Bella &lNoob"));
        assertEquals(BELLA, NameKey.of("§aBella Noob"));
        assertEquals(BELLA, NameKey.of("  BELLA    noob  "));
        assertEquals(BELLA, NameKey.of("Bella Noob"));
    }

    @Test
    void caractereInvisivelNaoDiferenciaNome() {
        assertEquals(BELLA, NameKey.of("Bel​la Noob"));
        assertEquals(BELLA, NameKey.of("‮Bella Noob"));
        assertEquals(BELLA, NameKey.of("Bella⁠ Noob️"));
    }

    @Test
    void acentoLarguraCheiaELetraCirilicaNaoDiferenciam() {
        assertEquals(BELLA, NameKey.of("Bélla Nõob"));
        assertEquals(BELLA, NameKey.of("Ｂｅｌｌａ Noob"));
        assertEquals(BELLA, NameKey.of("Bеlla Nооb"));
    }

    @Test
    void nomeSoDeInvisiveisFicaVazio() {
        assertEquals("", NameKey.of("​‌ㅤ "));
        assertEquals("", NameKey.of("&6&l"));
    }

    @Test
    void nomesDiferentesContinuamDiferentes() {
        assertFalse(NameKey.of("Bella Noob").equals(NameKey.of("Bela Noob")));
        assertFalse(NameKey.of("Bella Noob").equals(NameKey.of("BellaNoob")));
    }

    @Test
    void caractereEscondidoEDetectado() {
        assertTrue(NameKey.hasHiddenCharacters("Bella​ Noob"));
        assertTrue(NameKey.hasHiddenCharacters("Bella‮ Noob"));
        assertTrue(NameKey.hasHiddenCharacters("Bella\tNoob"));
        assertFalse(NameKey.hasHiddenCharacters("Bélla Noob"));
    }
}
