package com.aurorion.core.character;

import com.aurorion.core.data.PlayerMapNbt;
import com.aurorion.core.data.SavedDataAccess;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Account UUID authenticates the player; character UUID identifies this life in the story.
 * Death is terminal. A future character-creation transaction must reset progression before
 * replacing the current character; granting lives alone must never perform that transaction.
 */
public final class CharacterData extends SavedData {
    private static final SavedDataAccess<CharacterData> ACCESS = new SavedDataAccess<>(
            "aurorion_core_characters", CharacterData::new, CharacterData::load);
    public record Character(UUID id, long createdAt, long diedAt) {
        public boolean dead() { return diedAt > 0; }
    }
    private final Map<UUID, Character> current = new HashMap<>();

    public static CharacterData get(MinecraftServer server) { return ACCESS.get(server); }

    public Character current(UUID account) {
        Character character = current.get(account);
        if (character == null) {
            character = new Character(UUID.randomUUID(), System.currentTimeMillis(), 0);
            current.put(account, character);
            setDirty();
        }
        return character;
    }

    public boolean isDead(UUID account) {
        Character character = current.get(account);
        return character != null && character.dead();
    }

    public boolean markDead(UUID account) {
        Character before = current(account);
        if (before.dead()) return false;
        current.put(account, new Character(before.id(), before.createdAt(), System.currentTimeMillis()));
        setDirty();
        return true;
    }

    public Map<UUID, Character> characters() { return java.util.Collections.unmodifiableMap(current); }

    static CharacterData load(CompoundTag tag, HolderLookup.Provider registries) {
        CharacterData data = new CharacterData();
        PlayerMapNbt.read(tag, "Characters", data.current, entry -> new Character(
                entry.getUUID("CharacterId"), entry.getLong("CreatedAt"), entry.getLong("DiedAt")));
        return data;
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("Characters", PlayerMapNbt.write(current, (entry, character) -> {
            entry.putUUID("CharacterId", character.id());
            entry.putLong("CreatedAt", character.createdAt());
            entry.putLong("DiedAt", character.diedAt());
        }));
        return tag;
    }
}
