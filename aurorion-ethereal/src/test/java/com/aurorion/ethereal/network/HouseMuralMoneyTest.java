package com.aurorion.ethereal.network;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A tela do cofre lê o valor com a mesma regra do {@code Money.parse} da economia. */
class HouseMuralMoneyTest {
    @Test
    void readsObolosWithOneFragmentDigit() {
        assertEquals(120L, HouseMuralPayloads.parseMoney("12"));
        assertEquals(125L, HouseMuralPayloads.parseMoney("12,5"));
        assertEquals(125L, HouseMuralPayloads.parseMoney("12.5"));
        assertEquals(5L, HouseMuralPayloads.parseMoney(",5"));
    }

    @Test
    void refusesInsteadOfRounding() {
        assertEquals(-1L, HouseMuralPayloads.parseMoney(""));
        assertEquals(-1L, HouseMuralPayloads.parseMoney("0"));
        assertEquals(-1L, HouseMuralPayloads.parseMoney("12,55"));
        assertEquals(-1L, HouseMuralPayloads.parseMoney("12,"));
        assertEquals(-1L, HouseMuralPayloads.parseMoney("abc"));
        assertEquals(-1L, HouseMuralPayloads.parseMoney("9999999999999999"));
    }

    @Test
    void formatsLikeThePhone() {
        assertEquals("12,5 O", HouseMuralPayloads.formatMoney(125L));
        assertEquals("0,0 O", HouseMuralPayloads.formatMoney(0L));
    }
}
