package com.aurorion.core.character;

import org.junit.jupiter.api.Test;

import static com.aurorion.core.character.SheetCheck.Outcome.*;
import static org.junit.jupiter.api.Assertions.*;

class SheetCheckTest {
    @Test void bandsAroundTheDifficulty() {
        assertEquals(SUCCESS, SheetCheck.judge(6, 4, 10, 8));
        assertEquals(FAIL, SheetCheck.judge(5, 4, 10, 8));
        assertEquals(CRIT, SheetCheck.judge(16, 4, 10, 8));
        assertEquals(CRIT_FAIL, SheetCheck.judge(2, 0, 12, 8));
        assertEquals(CRIT, SheetCheck.judge(20, -50, 99, 8));
    }

    @Test void naturalOneOnlyBreaksHardChecks() {
        assertEquals(CRIT_FAIL, SheetCheck.judge(1, 10, 8, 8));
        assertEquals(SUCCESS, SheetCheck.judge(1, 3, 2, 8));
        assertEquals(0.0, SheetCheck.failChance(1, 2, false, 8), 1e-9);
    }

    @Test void chancesMatchTheDie() {
        // bonus 4 contra CD 10: falha com 1..5 → 25%
        assertEquals(0.25, SheetCheck.failChance(4, 10, false, 8), 1e-9);
        // vantagem: falha só se os dois dados ficam em 1..5 → (5/20)^2
        assertEquals(0.0625, SheetCheck.failChance(4, 10, true, 8), 1e-9);
        // crítico com 16..20 → 25%
        assertEquals(0.25, SheetCheck.critChance(4, 10, false, 8), 1e-9);
    }

    @Test void chancesAreProbabilities() {
        for (int bonus = -5; bonus <= 15; bonus++) {
            for (int dc = 0; dc <= 30; dc++) {
                double fail = SheetCheck.failChance(bonus, dc, true, 8);
                double crit = SheetCheck.critChance(bonus, dc, true, 8);
                assertTrue(fail >= 0 && crit >= 0 && fail + crit <= 1 + 1e-9);
            }
        }
    }
}
