package com.aurorion.limbo.exile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class LimboDataTest {
    @Test void countdownMarksAutosaveDirtyAndSurvivesReload() {
        LimboData data = new LimboData();
        UUID id = UUID.randomUUID();
        ExileRecord record = new ExileRecord(0, 48 * 3_600_000L, "Dev1");
        data.open(id, record);
        data.setDirty(false);
        data.drain(record, 3000);
        assertTrue(data.isDirty());
        LimboData restored = LimboData.load(data.save(new CompoundTag(), null), null);
        assertEquals(48 * 3_600_000L - 3000, restored.record(id).remainingMillis());
    }

    @Test void sessionClockDoesNotChargeDowntimeOrWallClockJumps() {
        LimboData data = new LimboData();
        assertEquals(0, data.elapsed(10_000));
        assertEquals(1000, data.elapsed(11_000));
        assertEquals(5000, data.elapsed(1_000_000));
        assertEquals(0, data.elapsed(900_000));
        CompoundTag saved = data.save(new CompoundTag(), null);
        saved.putLong("LastTick", 1); // formato da versao anterior
        LimboData restored = LimboData.load(saved, null);
        assertEquals(0, restored.elapsed(900_000_000));
        assertEquals(1000, restored.elapsed(900_001_000));
    }

    @Test void emptyOrExpiredRecordsDoNotDirtySaveEverySecond() {
        LimboData data = new LimboData();
        data.elapsed(1000);
        data.elapsed(2000);
        data.drain(new ExileRecord(0, 0, "Dev1"), 1000);
        assertFalse(data.isDirty());
    }

    @Test void pendingCleanupAndForgottenHistorySurviveRestart() {
        LimboData data = new LimboData();
        UUID id = UUID.randomUUID();
        DoorFrame frame = new DoorFrame(new BlockPos(2, 5, 9), Direction.SOUTH,
                "minecraft:crying_obsidian", "aurorion_limbo:limbo");
        data.deferDoor(frame);
        data.addForgottenExit(id, "Dev1");
        data.addForgottenExit(id, "Dev1");
        LimboData restored = LimboData.load(data.save(new CompoundTag(), null), null);
        assertEquals(2, restored.forgottenExits(id));
        assertEquals("Dev1", restored.forgottenName(id));
        assertEquals(frame, restored.pendingDoors().values().iterator().next());
        assertTrue(restored.active().isEmpty());
    }

    @Test void frameOnlyContainsTenEdgesInEitherOrientation() {
        for (Direction facing : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
            DoorFrame frame = new DoorFrame(BlockPos.ZERO, facing, "minecraft:crying_obsidian", "aurorion_limbo:limbo");
            java.util.Set<BlockPos> positions = new java.util.HashSet<>();
            for (int w = -1; w <= 1; w++) {
                for (int h = 0; h <= 3; h++) {
                    if (DoorFrame.edge(w, h)) positions.add(frame.position(w, h));
                }
            }
            assertEquals(10, positions.size());
            assertFalse(positions.contains(new BlockPos(0, 1, 0)));
            assertFalse(positions.contains(new BlockPos(0, 2, 0)));
            assertTrue(positions.contains(frame.base().above(3)));
        }
    }
}
