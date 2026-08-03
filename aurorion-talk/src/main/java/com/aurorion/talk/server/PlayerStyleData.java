package com.aurorion.talk.server;

import com.aurorion.talk.AurorionTalk;
import com.aurorion.talk.style.BalloonStyle;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Estilos escolhidos pelos jogadores na GUI, persistidos no {@code data/} do mundo.
 * Guardado no overworld — vale para o servidor inteiro, nao por dimensao.
 */
public class PlayerStyleData extends SavedData {
    private static final String FILE_ID = AurorionTalk.MOD_ID + "_styles";
    private static final String KEY_ENTRIES = "Entries";
    private static final String KEY_PLAYER = "Player";
    private static final String KEY_STYLE = "Style";

    private final Map<UUID, BalloonStyle> styles = new HashMap<>();

    public static PlayerStyleData get(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) throw new IllegalStateException("Overworld ainda nao carregado");

        return overworld.getDataStorage().computeIfAbsent(
                new Factory<>(PlayerStyleData::new, PlayerStyleData::load),
                FILE_ID
        );
    }

    private static PlayerStyleData load(CompoundTag tag, HolderLookup.Provider registries) {
        PlayerStyleData data = new PlayerStyleData();
        ListTag entries = tag.getList(KEY_ENTRIES, Tag.TAG_COMPOUND);

        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            if (!entry.hasUUID(KEY_PLAYER)) continue;

            BalloonStyle.load(entry.getCompound(KEY_STYLE))
                    .ifPresent(style -> data.styles.put(entry.getUUID(KEY_PLAYER), style));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag entries = new ListTag();

        styles.forEach((player, style) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID(KEY_PLAYER, player);
            entry.put(KEY_STYLE, style.save());
            entries.add(entry);
        });

        tag.put(KEY_ENTRIES, entries);
        return tag;
    }

    @Nullable
    public BalloonStyle getStyle(UUID player) {
        return styles.get(player);
    }

    public Map<UUID, BalloonStyle> all() {
        return Collections.unmodifiableMap(styles);
    }

    /** @return true se algo mudou de fato. */
    public boolean setStyle(UUID player, @Nullable BalloonStyle style) {
        BalloonStyle previous = style == null ? styles.remove(player) : styles.put(player, style);
        boolean changed = !Objects.equals(previous, style);

        if (changed) setDirty();
        return changed;
    }
}
