package com.aurorion.trama.progression;

import com.aurorion.core.data.PlayerMapNbt;
import com.aurorion.core.data.SavedDataAccess;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** Account index, character lifetime. Point source is set absolutely, never incremented on login. */
public final class ProgressData extends SavedData {
    private static final SavedDataAccess<ProgressData> ACCESS = new SavedDataAccess<>(
            "aurorion_trama_progress", ProgressData::new, ProgressData::load);
    public static final class Entry {
        public UUID character;
        public String origin = "";
        public long experience, activeSeconds, practiceDay = -1, lastRespec, combatUntil;
        public int dailyPracticeXp, earned = ProgressRules.INITIAL_POINTS;
        public boolean eraseCategory = true;
        public final Set<String> biomes = new HashSet<>(), advancements = new HashSet<>();
        public Entry(UUID character) { this.character = character; }
    }
    private final Map<UUID, Entry> players = new HashMap<>();
    public static ProgressData get(MinecraftServer server) { return ACCESS.get(server); }
    public Entry find(UUID account) { return players.get(account); }
    public Entry replace(UUID account, UUID character) {
        Entry entry = new Entry(character);
        players.put(account, entry); setDirty(); return entry;
    }
    private static ProgressData load(CompoundTag tag, HolderLookup.Provider registries) {
        var data = new ProgressData();
        PlayerMapNbt.read(tag, "Players", data.players, e -> {
            if (!e.hasUUID("Character")) return null;
            var entry = new Entry(e.getUUID("Character"));
            entry.origin = e.getString("Origin");
            entry.experience = Math.max(0, e.getLong("Experience"));
            entry.activeSeconds = Math.max(0, e.getLong("ActiveSeconds"));
            entry.practiceDay = e.getLong("PracticeDay");
            entry.dailyPracticeXp = Math.max(0, e.getInt("DailyPracticeXp"));
            entry.earned = Math.max(5, Math.min(45, e.getInt("Earned")));
            entry.lastRespec = Math.max(0, e.getLong("LastRespec"));
            entry.combatUntil = Math.max(0, e.getLong("CombatUntil"));
            entry.eraseCategory = e.getBoolean("EraseCategory");
            readStrings(e, "Biomes", entry.biomes); readStrings(e, "Advancements", entry.advancements);
            return entry;
        });
        return data;
    }
    private static void readStrings(CompoundTag tag, String key, Set<String> target) {
        var list = tag.getList(key, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) target.add(list.getString(i));
    }
    private static ListTag strings(Set<String> values) {
        var list = new ListTag(); values.stream().sorted().forEach(v -> list.add(StringTag.valueOf(v))); return list;
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("Players", PlayerMapNbt.write(players, (e, entry) -> {
            e.putUUID("Character", entry.character); e.putString("Origin", entry.origin);
            e.putLong("Experience", entry.experience); e.putLong("ActiveSeconds", entry.activeSeconds);
            e.putLong("PracticeDay", entry.practiceDay); e.putInt("DailyPracticeXp", entry.dailyPracticeXp);
            e.putInt("Earned", entry.earned); e.putLong("LastRespec", entry.lastRespec);
            e.putLong("CombatUntil", entry.combatUntil);
            e.putBoolean("EraseCategory", entry.eraseCategory);
            e.put("Biomes", strings(entry.biomes)); e.put("Advancements", strings(entry.advancements));
        }));
        return tag;
    }
}
