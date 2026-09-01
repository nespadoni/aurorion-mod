package com.aurorion.portais.pass;

import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransitPassTest {
    @Test
    void validityHonorsExpiryAndRemainingUses() {
        TransitPass pass = new TransitPass(Level.NETHER, 2_000L, 2);

        assertTrue(pass.isValidAt(1_999L));
        assertFalse(pass.isValidAt(2_000L));
        assertFalse(new TransitPass(Level.NETHER, TransitPass.NO_EXPIRY, 0).isValidAt(1L));
    }

    @Test
    void finitePassDisappearsAfterItsLastUse() {
        TransitPass lastUse = new TransitPass(Level.NETHER, TransitPass.NO_EXPIRY, 1);

        assertNull(lastUse.consumed());
    }

    @Test
    void unlimitedPassIsNotReallocatedWhenConsumed() {
        TransitPass unlimited = new TransitPass(
                Level.NETHER, TransitPass.NO_EXPIRY, TransitPass.UNLIMITED_USES);

        assertSame(unlimited, unlimited.consumed());
    }
}
