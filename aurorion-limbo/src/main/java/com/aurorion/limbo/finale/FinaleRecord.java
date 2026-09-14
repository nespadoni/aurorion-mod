package com.aurorion.limbo.finale;

import net.minecraft.nbt.CompoundTag;
import java.util.UUID;

/** Only viewing time advances this clock; relogging never restarts it. */
public final class FinaleRecord {
    private final UUID characterId;
    private final FinaleScript script;
    private long elapsed;
    private long lastViewingAt;
    public FinaleRecord(UUID characterId, FinaleScript script) { this.characterId = characterId; this.script = script; }
    public UUID characterId() { return characterId; }
    public FinaleScript script() { return script; }
    public long elapsed() { return elapsed; }
    public boolean finished() { return elapsed >= script.totalMillis(); }
    public void resumeViewing(long monotonicMillis) { lastViewingAt = monotonicMillis; }
    public void advanceViewing(long monotonicMillis) {
        advance(monotonicMillis - lastViewingAt);
        lastViewingAt = monotonicMillis;
    }
    public void advance(long millis) { elapsed = Math.min(script.totalMillis(), elapsed + Math.clamp(millis, 0, 5000)); }
    public void save(CompoundTag tag) {
        tag.putUUID("Character", characterId); tag.putLong("Elapsed", elapsed); tag.put("Script", script.save());
    }
    public static FinaleRecord load(CompoundTag tag) {
        var record = new FinaleRecord(tag.getUUID("Character"), FinaleScript.load(tag.getCompound("Script")));
        record.elapsed = Math.clamp(tag.getLong("Elapsed"), 0, record.script.totalMillis());
        return record;
    }
}
