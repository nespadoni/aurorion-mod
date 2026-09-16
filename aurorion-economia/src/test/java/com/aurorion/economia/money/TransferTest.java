package com.aurorion.economia.money;

import com.aurorion.economia.money.Transfer.Result;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TransferTest {
    @Test
    void allowsATransferThePayerCanAfford() {
        assertEquals(Result.OK, Transfer.check(100, 0, 100, false));
        assertEquals(Result.OK, Transfer.check(100, 0, 1, false));
    }

    @Test
    void refusesMoreThanThePayerHas() {
        assertEquals(Result.INSUFFICIENT, Transfer.check(99, 0, 100, false));
        assertEquals(Result.INSUFFICIENT, Transfer.check(0, 0, 1, false));
    }

    @Test
    void refusesZeroAndNegativeAmounts() {
        assertEquals(Result.INVALID_AMOUNT, Transfer.check(100, 0, 0, false));
        assertEquals(Result.INVALID_AMOUNT, Transfer.check(100, 0, -5, false));
    }

    @Test
    void refusesPayingYourself() {
        assertEquals(Result.SAME_ACCOUNT, Transfer.check(100, 100, 10, true));
    }

    /** O teto e checado por subtracao para nunca somar e estourar antes de decidir. */
    @Test
    void refusesOverflowingTheTarget() {
        assertEquals(Result.TARGET_FULL, Transfer.check(Money.MAX, Money.MAX, 1, false));
        assertEquals(Result.OK, Transfer.check(Money.MAX, Money.MAX - 1, 1, false));
    }
}
