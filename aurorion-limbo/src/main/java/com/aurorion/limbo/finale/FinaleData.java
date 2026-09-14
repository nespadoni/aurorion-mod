package com.aurorion.limbo.finale;

import com.aurorion.core.data.PlayerMapNbt;
import com.aurorion.core.data.SavedDataAccess;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Pending viewings only. Completed deaths remain in CharacterData, outside the sweep. */
public final class FinaleData extends SavedData {
    private static final SavedDataAccess<FinaleData> ACCESS = new SavedDataAccess<>(
            "aurorion_limbo_finales", FinaleData::new, FinaleData::load);
    private final Map<UUID, FinaleRecord> pending = new HashMap<>();
    public static FinaleData get(MinecraftServer server) { return ACCESS.get(server); }
    public FinaleRecord record(UUID account) { return pending.get(account); }
    public void put(UUID account, FinaleRecord record) { pending.put(account, record); setDirty(); }
    public void complete(UUID account) { pending.remove(account); setDirty(); }
    static FinaleData load(CompoundTag tag, HolderLookup.Provider registries) {
        var data = new FinaleData();
        PlayerMapNbt.read(tag, "Pending", data.pending, FinaleRecord::load);
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("Pending", PlayerMapNbt.write(pending, (entry, record) -> record.save(entry)));
        return tag;
    }
}
