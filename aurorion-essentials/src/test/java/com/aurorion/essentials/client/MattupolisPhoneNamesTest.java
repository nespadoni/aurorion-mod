package com.aurorion.essentials.client;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * O portao de remontagem do indice de nicks do telefone.
 *
 * <p>Existe por causa de um bug que chegou ao servidor: a primeira montagem era forcada com um
 * instante inicial {@code Long.MIN_VALUE}, e a subtracao estourava o {@code long}. O indice ficava
 * vazio para sempre e o celular mostrava o nick de todo mundo, sem erro nenhum no log.</p>
 */
class MattupolisPhoneNamesTest {
    private static final long INTERVAL = 1000L;

    @Test
    void buildsTheIndexOnTheFirstCall() {
        assertTrue(MattupolisPhoneNames.needsRefresh(false, 0L, 0L));
    }

    @Test
    void doesNotRebuildWithinTheInterval() {
        assertFalse(MattupolisPhoneNames.needsRefresh(true, 10_000L, 10_000L + INTERVAL - 1));
    }

    @Test
    void rebuildsOnceTheIntervalHasPassed() {
        assertTrue(MattupolisPhoneNames.needsRefresh(true, 10_000L, 10_000L + INTERVAL));
    }

    /** O relogio do jogo e {@code System.nanoTime()/1e6}: a origem e arbitraria, inclusive baixa. */
    @Test
    void theFirstBuildDoesNotDependOnTheClockOrigin() {
        for (long now : new long[]{0L, 1L, 10_000_000L, Long.MAX_VALUE / 2}) {
            assertTrue(MattupolisPhoneNames.needsRefresh(false, 0L, now), "now=" + now);
        }
    }

    private static final Function<String, String> NAMES = names(Map.of("Steve_42", "Arthur Pendragon", "Alex", "Morgana"));

    @Test
    void replacesTheNickInsideASentence() {
        assertEquals("Arthur Pendragon - Oi!", MattupolisPhoneNames.replaceTokens("Steve_42 - Oi!", NAMES));
        assertEquals("★ Morgana", MattupolisPhoneNames.replaceTokens("★ Alex", NAMES));
        assertEquals("Morgana is offline.", MattupolisPhoneNames.replaceTokens("alex is offline.", NAMES));
    }

    /** Nick colado em outra palavra nao e o nick: "Alexandre" nao vira "Morganaandre". */
    @Test
    void onlyReplacesWholeWords() {
        assertEquals("Alexandre e Steve_420", MattupolisPhoneNames.replaceTokens("Alexandre e Steve_420", NAMES));
    }

    /** Sem troca, a mesma instancia: o cache de textos nao guarda copias. */
    @Test
    void returnsTheSameInstanceWhenNothingChanges() {
        String text = "Sem nick nenhum aqui.";
        assertSame(text, MattupolisPhoneNames.replaceTokens(text, NAMES));
    }

    @Test
    void twitterHandleFollowsTheCharacterName() {
        assertEquals("joao_da_silva", MattupolisPhoneNames.handleOf("João da Silva"));
        assertEquals("personagem", MattupolisPhoneNames.handleOf("★★"));
    }

    private static Function<String, String> names(Map<String, String> byNick) {
        Map<String, String> index = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        index.putAll(byNick);
        return index::get;
    }
}
