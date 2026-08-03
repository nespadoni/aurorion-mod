package com.aurorion.utils.abduction;

import com.aurorion.utils.AurorionUtils;
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
import java.util.UUID;

/**
 * De onde cada jogador foi abduzido pela ultima vez, para o {@code /abduzir voltar} funcionar
 * mesmo depois de um restart do servidor. Mesma forma de {@code FakeNameData} do
 * aurorion-essentials: {@link SavedData} anexada a overworld, indexada por UUID.
 */
public class AbductionOriginData extends SavedData {
    private static final String FILE_ID = AurorionUtils.MOD_ID + "_abduction_origins";
    private static final String KEY_ENTRIES = "Entries";
    private static final String KEY_PLAYER = "Player";
    private static final String KEY_SPOT = "Spot";

    private final Map<UUID, TeleportSpot> origins = new HashMap<>();

    public static AbductionOriginData get(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) throw new IllegalStateException("Overworld ainda nao carregado");

        return overworld.getDataStorage().computeIfAbsent(
                new Factory<>(AbductionOriginData::new, AbductionOriginData::load),
                FILE_ID
        );
    }

    private static AbductionOriginData load(CompoundTag tag, HolderLookup.Provider registries) {
        AbductionOriginData data = new AbductionOriginData();
        ListTag entries = tag.getList(KEY_ENTRIES, Tag.TAG_COMPOUND);

        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            if (!entry.hasUUID(KEY_PLAYER)) continue;

            data.origins.put(entry.getUUID(KEY_PLAYER), TeleportSpot.load(entry.getCompound(KEY_SPOT)));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag entries = new ListTag();

        origins.forEach((player, spot) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID(KEY_PLAYER, player);
            entry.put(KEY_SPOT, spot.save(new CompoundTag()));
            entries.add(entry);
        });

        tag.put(KEY_ENTRIES, entries);
        return tag;
    }

    @Nullable
    public TeleportSpot get(UUID player) {
        return origins.get(player);
    }

    public void set(UUID player, TeleportSpot spot) {
        origins.put(player, spot);
        setDirty();
    }

    /** @return o spot removido, ou null se o jogador nao tinha nenhuma origem salva. */
    @Nullable
    public TeleportSpot clear(UUID player) {
        TeleportSpot removed = origins.remove(player);
        if (removed != null) setDirty();
        return removed;
    }
}
