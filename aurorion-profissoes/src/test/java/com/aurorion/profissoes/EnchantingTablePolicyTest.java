package com.aurorion.profissoes;

import com.aurorion.profissoes.data.Profession;
import com.aurorion.profissoes.server.EnchantingTablePolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EnchantingTablePolicyTest {
    @Test void nonArcanistsCanOnlyUseTheFirstTwoOptions() {
        assertTrue(EnchantingTablePolicy.canUseOption(Profession.NONE, 0));
        assertTrue(EnchantingTablePolicy.canUseOption(Profession.SMITH, 1));
        assertFalse(EnchantingTablePolicy.canUseOption(Profession.CHEF, 2));
    }

    @Test void arcanistsCanUseAllThreeOptions() {
        assertTrue(EnchantingTablePolicy.canUseOption(Profession.ARCANIST, 0));
        assertTrue(EnchantingTablePolicy.canUseOption(Profession.ARCANIST, 1));
        assertTrue(EnchantingTablePolicy.canUseOption(Profession.ARCANIST, 2));
    }

    @Test void invalidOptionsAreRejected() {
        assertFalse(EnchantingTablePolicy.canUseOption(Profession.ARCANIST, -1));
        assertFalse(EnchantingTablePolicy.canUseOption(Profession.ARCANIST, 3));
    }
}
