package com.aurorion.intencao.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DuelReactionTest {
    private static Duel.Reaction parry(int skill, boolean perfect, int streak, int dc, int die) {
        return Duel.reaction(true, skill, 0, perfect, streak, dc, die, 5, 2, 2);
    }

    @Test void timingAloneDoesNotBlock() {
        // Clicou no tempo, mas o dado e o atributo não chegam ao ataque: o golpe inteiro entra.
        Duel.Reaction r = parry(1, false, 0, 18, 8);
        assertFalse(r.success());
        assertEquals(12, r.damage(12));
    }

    @Test void parryHalvesAndPerfectWideMarginCounters() {
        Duel.Reaction half = parry(4, false, 0, 14, 10);
        assertTrue(half.success());
        assertFalse(half.counter());
        assertEquals(6, half.damage(12));
        Duel.Reaction counter = parry(4, true, 0, 10, 13);   // 13 + 4 + 5 = 22, passou por 12
        assertTrue(counter.counter());
        assertEquals(0, counter.damage(12));
        Duel.Reaction narrow = parry(4, true, 0, 18, 13);    // 22 contra 18: só metade
        assertFalse(narrow.counter());
        assertEquals(6, narrow.damage(12));
    }

    @Test void guardTiresWithEachDefense() {
        assertTrue(parry(3, false, 0, 15, 10).success());   // 15
        assertFalse(parry(3, false, 1, 15, 10).success());  // 13
        assertEquals(4, parry(3, false, 2, 15, 10).fatigue());
    }

    @Test void dodgeIsAllOrNothing() {
        Duel.Reaction ok = Duel.reaction(false, 0, 5, false, 0, 15, 8, 5, 2, 2);
        assertEquals(0, ok.damage(10));
        Duel.Reaction miss = Duel.reaction(false, 0, 5, false, 0, 16, 8, 5, 2, 2);
        assertEquals(10, miss.damage(10));
    }

    @Test void naturalOneAndTwenty() {
        assertFalse(parry(30, true, 0, 5, 1).success());
        assertTrue(parry(-10, false, 5, 40, 20).success());
    }

    @Test void clientTimingIsCheckedAgainstArrival() {
        // Clicou no instante (0) e o pacote chegou 300 ms depois: latência normal, aceito.
        assertEquals(0, Duel.plausibleOffset(0, 300));
        // Diz "0" mas o pacote chegou 900 ms depois do impacto: mentira ou atraso, fora da janela.
        assertTrue(Duel.plausibleOffset(0, 900) > 10_000);
        // Diz que clicou depois de o pacote chegar: impossível.
        assertTrue(Duel.plausibleOffset(400, 100) > 10_000);
        // Valor extremo não passa por "perfeito" (Math.abs(MIN_VALUE) seria negativo).
        assertTrue(Duel.plausibleOffset(Integer.MIN_VALUE, -100) > 10_000);
    }
}
