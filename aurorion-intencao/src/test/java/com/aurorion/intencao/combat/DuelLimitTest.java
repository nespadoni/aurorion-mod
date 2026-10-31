package com.aurorion.intencao.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DuelLimitTest {
    @Test void limitsFromTheChallenge() {
        assertEquals(-1, DuelManager.parseLimit("primeiro_sangue"));
        assertEquals(0, DuelManager.parseLimit("morte"));
        assertEquals(50, DuelManager.parseLimit("50"));
        assertEquals(25, DuelManager.parseLimit("25%"));
        assertEquals(-2, DuelManager.parseLimit("0"));
        assertEquals(-2, DuelManager.parseLimit("100"));
        assertEquals(-2, DuelManager.parseLimit("talvez"));
    }

    @Test void describedForTheChat() {
        assertEquals("primeiro sangue", DuelManager.describe(-1));
        assertEquals("ATÉ A MORTE", DuelManager.describe(0));
        assertEquals("até 10% da vida", DuelManager.describe(10));
    }
}
