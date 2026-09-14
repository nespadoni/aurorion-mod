package com.aurorion.limbo.finale;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class FinaleRecordTest {
    private static FinaleScript script() {
        return new FinaleScript(25, 15, 140, "Adeus", List.of("Uma historia."), "aurorion_limbo:finale");
    }

    @Test void finalTitleAlwaysHasSixtySecondsBeforeDisconnect() {
        FinaleRecord record = new FinaleRecord(UUID.randomUUID(), script());
        for (int second = 0; second < 180; second++) record.advance(1000);
        assertEquals(record.script().deathTitleMillis(), record.elapsed());
        assertFalse(record.finished());
        for (int second = 0; second < 59; second++) record.advance(1000);
        assertFalse(record.finished());
        record.advance(1000);
        assertTrue(record.finished());
        assertEquals(60_000, record.script().totalMillis() - record.script().deathTitleMillis());
    }

    @Test void disconnectAndRestartResumeTheSameCharacterAndScriptWithoutChargingOfflineTime() {
        UUID account = UUID.randomUUID(), character = UUID.randomUUID();
        FinaleData data = new FinaleData();
        FinaleRecord record = new FinaleRecord(character, script());
        data.put(account, record);
        record.resumeViewing(10_000);
        record.advanceViewing(13_000);
        FinaleData restored = FinaleData.load(data.save(new CompoundTag(), null), null);
        FinaleRecord resumed = restored.record(account);
        assertEquals(character, resumed.characterId());
        assertEquals(script(), resumed.script());
        resumed.resumeViewing(9_000_000);
        resumed.advanceViewing(9_001_000);
        assertEquals(4000, resumed.elapsed());
        restored.complete(account);
        assertNull(FinaleData.load(restored.save(new CompoundTag(), null), null).record(account));
    }

    @Test void clockJumpsAndStallsCannotSkipTheWholeEnding() {
        var record = new FinaleRecord(UUID.randomUUID(), script());
        record.resumeViewing(10_000);
        record.advanceViewing(1_000_000);
        assertEquals(5000, record.elapsed());
        record.advanceViewing(999_000);
        assertEquals(5000, record.elapsed());
    }

    @Test void storySnapshotIsImmutableAndBounded() {
        List<String> paragraphs = new ArrayList<>(List.of("Before reload"));
        var script = new FinaleScript(25, 15, 140, "Adeus", paragraphs, "aurorion_limbo:finale");
        paragraphs.set(0, "After reload");
        assertEquals("Before reload", script.paragraphs().getFirst());
        assertThrows(UnsupportedOperationException.class, () -> script.paragraphs().clear());
        var oversized = new FinaleScript(-1, -1, Integer.MAX_VALUE, "x".repeat(2000),
                java.util.Collections.nCopies(200, "x".repeat(2000)), "invalid resource");
        assertEquals(128, oversized.paragraphs().size());
        assertEquals(512, oversized.paragraphs().getFirst().length());
        assertEquals(512, oversized.phrase().length());
        assertEquals("aurorion_limbo:finale", oversized.music());
        assertEquals(60_000, oversized.totalMillis() - oversized.deathTitleMillis());
    }
}
