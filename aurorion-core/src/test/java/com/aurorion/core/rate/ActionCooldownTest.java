package com.aurorion.core.rate;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ActionCooldownTest {
    @Test void refusedAttemptsDoNotExtendTheWindowOrBlockOtherAccounts() {
        var cooldown = new ActionCooldown(30_000);
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        assertTrue(cooldown.acquire(first, 1_000));
        assertFalse(cooldown.acquire(first, 30_999));
        assertTrue(cooldown.acquire(second, 30_999));
        assertEquals(1, cooldown.remainingMillis(first, 30_999));
        assertTrue(cooldown.acquire(first, 31_000));
    }

    @Test void fullStorageDoesNotEvictActiveLimitsAndReusesExpiredSlots() {
        var cooldown = new ActionCooldown(100, 1);
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        assertTrue(cooldown.acquire(first, 0));
        assertFalse(cooldown.acquire(second, 99));
        assertFalse(cooldown.acquire(first, 99));
        assertTrue(cooldown.acquire(second, 100));
        cooldown.clear();
        assertTrue(cooldown.acquire(second, 101));
    }
}
