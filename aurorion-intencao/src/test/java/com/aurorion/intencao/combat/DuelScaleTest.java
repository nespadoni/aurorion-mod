package com.aurorion.intencao.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Os números do RPG v0.3 (§04 e §09), conferidos contra os exemplos do próprio documento. */
class DuelScaleTest {
    @Test void scaleIsReferenceLifeOver150() {
        assertEquals(250 / 150.0, Combatant.scaleFor(3), 1e-9);
        assertEquals(30050 / 150.0, Combatant.scaleFor(30), 1e-9);
    }

    @Test void documentExample() {
        // "rolagem bruta 40 + MO 6 resulta em piso(46 × 5/3) = 76"
        assertEquals(76, Combatant.damage(40, 6, Combatant.scaleFor(3)));
        // máximo normal do básico na alma 3: piso((36 + 6) × 5/3) = 70
        assertEquals(70, Combatant.damage(36, 6, Combatant.scaleFor(3)));
        // H5 máximo na alma 3: piso((144 + 6) × 5/3) = 250
        assertEquals(250, Combatant.damage(144, 6, Combatant.scaleFor(3)));
    }

    @Test void soulFromSheetOrNearestReferenceLife() {
        assertEquals(4, Combatant.soulFor(4, 9999));
        assertEquals(3, Combatant.soulFor(0, 250));
        assertEquals(4, Combatant.soulFor(0, 345));
        assertEquals(1, Combatant.soulFor(0, 20));
    }

    @Test void packets() {
        assertEquals(3, Combatant.BASIC_DICE);
        assertArrayEquals(new int[]{4, 5, 6, 8, 12}, Combatant.TECHNIQUE_DICE);
    }

    @Test void attackCritsOnlyOnNatural20() {
        assertEquals(com.aurorion.core.character.SheetCheck.Outcome.SUCCESS, Duel.attackRoll(19, 10, 13));
        assertEquals(com.aurorion.core.character.SheetCheck.Outcome.CRIT, Duel.attackRoll(20, -5, 30));
        assertEquals(com.aurorion.core.character.SheetCheck.Outcome.CRIT_FAIL, Duel.attackRoll(1, 30, 5));
        assertEquals(com.aurorion.core.character.SheetCheck.Outcome.FAIL, Duel.attackRoll(5, 3, 13));
        assertEquals(com.aurorion.core.character.SheetCheck.Outcome.SUCCESS, Duel.attackRoll(10, 3, 13));
    }
}
