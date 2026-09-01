package com.aurorion.portais.pass;

import net.minecraft.world.level.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class PendingPassConsumptionsTest {
    private final UUID player = UUID.randomUUID();

    @AfterEach
    void clearPendingAuthorizations() {
        PendingPassConsumptions.clear();
    }

    @Test
    void consumesOnlyAfterTheAuthorizedDimensionChangeCompletes() {
        PendingPassConsumptions.authorize(player, Level.OVERWORLD, Level.NETHER, Level.NETHER);

        assertSame(Level.NETHER,
                PendingPassConsumptions.complete(player, Level.OVERWORLD, Level.NETHER));
        assertNull(PendingPassConsumptions.complete(player, Level.OVERWORLD, Level.NETHER));
    }

    @Test
    void canceledOrDifferentTravelDoesNotConsumeThePass() {
        PendingPassConsumptions.authorize(player, Level.OVERWORLD, Level.NETHER, Level.NETHER);

        assertNull(PendingPassConsumptions.complete(player, Level.OVERWORLD, Level.END));
        assertNull(PendingPassConsumptions.complete(player, Level.OVERWORLD, Level.NETHER));
    }

    @Test
    void endOfTickDropsAnAuthorizationForACanceledTravel() {
        PendingPassConsumptions.authorize(player, Level.OVERWORLD, Level.NETHER, Level.NETHER);

        PendingPassConsumptions.clear();

        assertNull(PendingPassConsumptions.complete(player, Level.OVERWORLD, Level.NETHER));
    }
}
