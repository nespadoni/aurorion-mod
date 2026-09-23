package com.aurorion.core.death;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class DeathClaimsTest {
    @Test
    void claimSurvivesRestartAndTwoRecallsAddUp() {
        UUID death = UUID.randomUUID();
        DeathClaims source = new DeathClaims();
        source.claim(death, "relicario", 3);
        source.claim(death, "relicario", 2);

        DeathClaims restored = DeathClaims.load(source.save(new CompoundTag(), null), null);
        DeathClaims.Claim claim = restored.find(death);
        assertNotNull(claim);
        assertEquals("relicario", claim.source());
        assertEquals(5, claim.stacks());
    }

    @Test
    void nothingReturnedIsNeverRecorded() {
        UUID death = UUID.randomUUID();
        DeathClaims data = new DeathClaims();
        data.claim(death, "relicario", 0);

        assertNull(data.find(death));
    }

    @Test
    void oldestClaimLeavesWhenTheCapIsReached() {
        DeathClaims data = new DeathClaims();
        UUID first = UUID.randomUUID();
        data.claim(first, "relicario", 1);
        for (int i = 0; i < DeathClaims.MAX_ENTRIES; i++) {
            data.claim(UUID.randomUUID(), "relicario", 1);
        }

        assertEquals(DeathClaims.MAX_ENTRIES, data.size());
        assertNull(data.find(first));
    }
}
