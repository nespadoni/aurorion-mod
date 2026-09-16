package com.aurorion.economia.money;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MoneyTest {
    @Test
    void splitsFragmentsIntoObolosAndFragments() {
        assertEquals(12, Money.obolos(123));
        assertEquals(3, Money.fragmentos(123));
        assertEquals(0, Money.obolos(7));
        assertEquals(7, Money.fragmentos(7));
        assertEquals(5, Money.obolos(50));
        assertEquals(0, Money.fragmentos(50));
    }

    @Test
    void formatsTheShortForm() {
        assertEquals("12,3o", Money.format(123));
        assertEquals("0,0o", Money.format(0));
        assertEquals("-1,2o", Money.format(-12));
    }

    @Test
    void describesInWordsWithAgreement() {
        assertEquals("12 óbolos e 3 fragmentos", Money.describe(123));
        assertEquals("1 óbolo e 1 fragmento", Money.describe(11));
        assertEquals("5 óbolos", Money.describe(50));
        assertEquals("7 fragmentos", Money.describe(7));
        assertEquals("1 fragmento", Money.describe(1));
        assertEquals("0 óbolos", Money.describe(0));
    }

    @Test
    void parsesWholeObolosAndTheFragmentDecimal() {
        assertEquals(120, Money.parse("12"));
        assertEquals(123, Money.parse("12,3"));
        assertEquals(123, Money.parse("12.3"), "teclado numerico manda ponto");
        assertEquals(5, Money.parse("0,5"));
        assertEquals(5, Money.parse(",5"));
        assertEquals(120, Money.parse("  12  "));
    }

    /** Arredondar calado faria o jogador pagar um valor diferente do que digitou. */
    @Test
    void refusesMoreThanOneDecimalInsteadOfRounding() {
        assertThrows(Money.MoneyFormatException.class, () -> Money.parse("12,34"));
        assertThrows(Money.MoneyFormatException.class, () -> Money.parse("12,"));
    }

    @Test
    void refusesAnythingThatIsNotAPositiveAmount() {
        for (String bad : new String[]{"", "   ", "abc", "-5", "12,a", "1 2", "12,3,4", null}) {
            assertThrows(Money.MoneyFormatException.class, () -> Money.parse(bad), "entrada: " + bad);
        }
        assertThrows(Money.MoneyFormatException.class, () -> Money.parse("0"));
        assertThrows(Money.MoneyFormatException.class, () -> Money.parse("0,0"));
    }

    /** Dois saldos validos tem que somar sem estourar — e o que toda transferencia faz. */
    @Test
    void theCeilingLeavesRoomToAddTwoBalances() {
        assertEquals(Long.MAX_VALUE / 2, Money.MAX);
        assertThrows(Money.MoneyFormatException.class, () -> Money.parse(Long.MAX_VALUE + "0"));
    }
}
