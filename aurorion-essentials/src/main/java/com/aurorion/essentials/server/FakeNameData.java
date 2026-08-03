package com.aurorion.essentials.server;

import com.aurorion.essentials.AurorionEssentials;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Nomes falsos persistidos entre reinicios do servidor, guardados como o texto cru digitado no
 * comando (com os codigos "&"). O {@link com.aurorion.essentials.fakename.FakeName} colorido e
 * sempre reconstruido em runtime a partir disso — nada de Component serializado em disco.
 */
public class FakeNameData extends SavedData {
    private static final String FILE_ID = AurorionEssentials.MOD_ID + "_fake_names";
    private static final String KEY_ENTRIES = "Entries";
    private static final String KEY_PLAYER = "Player";
    private static final String KEY_NAME = "Name";

    private final Map<UUID, String> names = new HashMap<>();

    public static FakeNameData get(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) throw new IllegalStateException("Overworld ainda nao carregado");

        return overworld.getDataStorage().computeIfAbsent(
                new Factory<>(FakeNameData::new, FakeNameData::load),
                FILE_ID
        );
    }

    private static FakeNameData load(CompoundTag tag, HolderLookup.Provider registries) {
        FakeNameData data = new FakeNameData();
        ListTag entries = tag.getList(KEY_ENTRIES, Tag.TAG_COMPOUND);

        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            if (!entry.hasUUID(KEY_PLAYER)) continue;

            data.names.put(entry.getUUID(KEY_PLAYER), entry.getString(KEY_NAME));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag entries = new ListTag();

        names.forEach((player, name) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID(KEY_PLAYER, player);
            entry.putString(KEY_NAME, name);
            entries.add(entry);
        });

        tag.put(KEY_ENTRIES, entries);
        return tag;
    }

    @Nullable
    public String getRaw(UUID player) {
        return names.get(player);
    }

    /** @return true se algo mudou de fato. */
    public boolean setRaw(UUID player, @Nullable String raw) {
        String previous = raw == null ? names.remove(player) : names.put(player, raw);
        boolean changed = !Objects.equals(previous, raw);

        if (changed) setDirty();
        return changed;
    }
}
