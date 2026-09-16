package com.aurorion.profissoes;

import com.aurorion.profissoes.compat.InjuryPolicy;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InjuryPolicyTest {
    @Test void ordinaryInjuriesAreNotClinicalTrauma() {
        assertFalse(InjuryPolicy.severe(3, 10, .35));
        assertTrue(InjuryPolicy.severe(7, 10, .35));
        assertFalse(InjuryPolicy.severe(0, 0, .35));
    }
    @Test void repeatedFirstAidStabilizesButCannotFinishSevereTreatment() {
        float damage = 9;
        for (int i = 0; i < 20; i++) damage -= InjuryPolicy.firstAid(2, damage, 10, .5);
        assertEquals(5, damage);
    }
    @Test void firstAidDoesNotHurtWhenOtherEffectsAlreadyRaisedHealth() {
        assertEquals(0, InjuryPolicy.firstAid(4, 2, 10, .5));
    }
}
