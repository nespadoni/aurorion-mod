package com.aurorion.core.character;

import com.aurorion.core.data.PlayerMapNbt;
import com.aurorion.core.data.SavedDataAccess;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Account authentication, character identity and reset journal have different lifetimes. */
public final class CharacterData extends SavedData {
    private static final SavedDataAccess<CharacterData> ACCESS = new SavedDataAccess<>(
            "aurorion_core_characters", CharacterData::new, CharacterData::load);
    public record Character(UUID id, long createdAt, long diedAt, String firstName, String lastName) {
        public Character(UUID id, long createdAt, long diedAt) { this(id, createdAt, diedAt, "", ""); }
        public boolean dead() { return diedAt > 0; }
        public boolean named() { return !firstName.isBlank() && !lastName.isBlank(); }
        public String fullName() { return (firstName + " " + lastName).strip(); }
    }
    public record Archived(UUID account, Character character) { }
    public record Pending(UUID account, UUID previousId, Character next, String accountName) { }
    private final Map<UUID, Character> current = new HashMap<>();
    private final Map<UUID, Archived> history = new HashMap<>();
    private final Map<UUID, Pending> pending = new HashMap<>();
    private final Map<String, UUID> names = new HashMap<>();
    /** Contas que a staff liberou para comecar outra historia. Consumida ao publicar a identidade. */
    private final Set<UUID> authorized = new HashSet<>();
    /** Identidades publicadas que ainda nao foram apresentadas ao dono — ele estava offline. */
    private final Set<UUID> newborn = new HashSet<>();

    public static CharacterData get(MinecraftServer server) { return ACCESS.get(server); }
    @Nullable public Character find(UUID account) { return current.get(account); }
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
    public boolean needsName(UUID account) {
        Character character = current.get(account);
        return character == null || !character.named() || character.dead() || pending.containsKey(account);
    }
    /** A name retires with the character that wore it: history keeps the key reserved. */
    public boolean nameAvailable(CharacterName name) { return nameAvailable(name, null); }
    public boolean nameAvailable(CharacterName name, @Nullable UUID selfCharacterId) {
        UUID owner = names.get(name.key());
        return owner == null || owner.equals(selfCharacterId);
    }
    public Map<UUID, Character> characters() { return Collections.unmodifiableMap(current); }
    public Map<UUID, Archived> history() { return Collections.unmodifiableMap(history); }
    @Nullable public Pending pending(UUID account) { return pending.get(account); }
    public Map<UUID, Pending> pendingResets() { return Collections.unmodifiableMap(pending); }

    // --- Autorizacao da staff -------------------------------------------------------------------

    /**
     * Morrer nao da direito a recomecar: alguem precisa abrir a porta.
     *
     * <p>Sem isto, a morte definitiva viraria uma inconveniencia de dois minutos — morre, cria outro,
     * segue. A conta continua valida e sem banimento; o que ela nao tem e permissao automatica para
     * comecar outra historia.
     */
    public boolean authorize(UUID account) {
        if (!authorized.add(account)) return false;
        setDirty();
        return true;
    }

    public boolean revokeAuthorization(UUID account) {
        if (!authorized.remove(account)) return false;
        setDirty();
        return true;
    }

    public boolean isAuthorized(UUID account) { return authorized.contains(account); }
    public Set<UUID> authorizations() { return Collections.unmodifiableSet(authorized); }

    /**
     * A identidade nova existe, mas o dono ainda nao foi apresentado a ela.
     *
     * <p>Publicar acontece com a pessoa <b>offline</b> — e o unico momento em que da para apagar o
     * arquivo dela. A saudacao, o nome exibido e o resto da apresentacao esperam aqui ate o login.
     */
    public boolean takeNewborn(UUID account) {
        if (!newborn.remove(account)) return false;
        setDirty();
        return true;
    }

    /** Naming an existing living, unnamed character preserves its ID and progression. */
    public Character nameLiving(UUID account, CharacterName name) {
        if (pending.containsKey(account) || isDead(account)) throw new IllegalStateException("Character requires reset");
        Character before = current(account);
        if (before.named() || !nameAvailable(name)) throw new IllegalStateException("Name unavailable");
        Character named = new Character(before.id(), before.createdAt(), 0, name.firstName(), name.lastName());
        current.put(account, named);
        names.put(name.key(), named.id());
        setDirty();
        return named;
    }

    /** Staff correction of spelling. Keeps the ID, the progression and the death mark. */
    public Character rename(UUID account, CharacterName name) {
        Character before = current.get(account);
        if (before == null || !nameAvailable(name, before.id())) throw new IllegalStateException("Name unavailable");
        if (before.named()) names.remove(CharacterName.key(before.fullName()));
        Character renamed = new Character(before.id(), before.createdAt(), before.diedAt(),
                name.firstName(), name.lastName());
        current.put(account, renamed);
        names.put(name.key(), renamed.id());
        setDirty();
        return renamed;
    }

    public boolean markDead(UUID account) {
        Character before = current(account);
        if (before.dead()) return false;
        current.put(account, new Character(before.id(), before.createdAt(), System.currentTimeMillis(),
                before.firstName(), before.lastName()));
        setDirty();
        return true;
    }

    /** Reserve an identity; this does not revive the account or remove its old character. */
    public Pending beginReplacement(UUID account, String accountName, CharacterName name) {
        Character before = current.get(account);
        if (before == null || !before.dead() || pending.containsKey(account) || !nameAvailable(name)
                || !authorized.contains(account))
            throw new IllegalStateException("Replacement is not eligible");
        Character next = new Character(UUID.randomUUID(), System.currentTimeMillis(), 0, name.firstName(), name.lastName());
        Pending transaction = new Pending(account, before.id(), next, accountName);
        pending.put(account, transaction);
        names.put(name.key(), next.id());
        setDirty();
        return transaction;
    }

    /** Caller must have durably reset all progression before publishing this identity. */
    public Character finishReplacement(UUID account, UUID expectedNext) {
        Pending transaction = pending.get(account);
        Character before = current.get(account);
        if (transaction == null || !transaction.next().id().equals(expectedNext)
                || before == null || !before.dead() || !before.id().equals(transaction.previousId()))
            throw new IllegalStateException("Stale reset transaction");
        history.put(before.id(), new Archived(account, before));
        current.put(account, transaction.next());
        pending.remove(account);
        // A autorizacao valia por uma historia. A proxima morte precisa de outra.
        authorized.remove(account);
        newborn.add(account);
        setDirty();
        return transaction.next();
    }

    /**
     * Give up a reservation whose reset never completed, freeing the name for another attempt.
     * The account stays dead: only {@link #finishReplacement} revives it.
     */
    @Nullable public Pending abandonReplacement(UUID account) {
        Pending transaction = pending.remove(account);
        if (transaction == null) return null;
        names.remove(CharacterName.key(transaction.next().fullName()));
        setDirty();
        return transaction;
    }

    /** Restore the journal if committing to disk failed, keeping the account closed. */
    public void restorePending(Pending transaction, Character previous) {
        UUID account = transaction.account();
        current.put(account, previous);
        pending.put(account, transaction);
        history.remove(previous.id());
        newborn.remove(account);
        authorized.add(account);
        setDirty();
    }

    static CharacterData load(CompoundTag tag, HolderLookup.Provider registries) {
        CharacterData data = new CharacterData();
        PlayerMapNbt.read(tag, "Characters", data.current, CharacterData::readCharacter);
        PlayerMapNbt.read(tag, "History", data.history, e -> new Archived(e.getUUID("Account"), readCharacter(e)));
        PlayerMapNbt.read(tag, "Pending", data.pending, e -> new Pending(e.getUUID("Player"),
                e.getUUID("Previous"), readCharacter(e), e.getString("AccountName")));
        PlayerMapNbt.readSet(tag, "Authorized", data.authorized);
        PlayerMapNbt.readSet(tag, "Newborn", data.newborn);
        data.current.values().forEach(data::index);
        data.history.values().forEach(a -> data.index(a.character()));
        data.pending.values().forEach(p -> data.index(p.next()));
        return data;
    }
    private void index(Character character) {
        if (character.named()) names.put(CharacterName.key(character.fullName()), character.id());
    }
    private static Character readCharacter(CompoundTag e) {
        return new Character(e.getUUID("CharacterId"), e.getLong("CreatedAt"), e.getLong("DiedAt"),
                e.getString("FirstName"), e.getString("LastName"));
    }
    private static void writeCharacter(CompoundTag e, Character character) {
        e.putUUID("CharacterId", character.id()); e.putLong("CreatedAt", character.createdAt());
        e.putLong("DiedAt", character.diedAt()); e.putString("FirstName", character.firstName());
        e.putString("LastName", character.lastName());
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("Characters", PlayerMapNbt.write(current, CharacterData::writeCharacter));
        tag.put("History", PlayerMapNbt.write(history, (e, a) -> {
            e.putUUID("Account", a.account()); writeCharacter(e, a.character());
        }));
        tag.put("Pending", PlayerMapNbt.write(pending, (e, p) -> {
            e.putUUID("Previous", p.previousId()); e.putString("AccountName", p.accountName()); writeCharacter(e, p.next());
        }));
        tag.put("Authorized", PlayerMapNbt.writeSet(authorized));
        tag.put("Newborn", PlayerMapNbt.writeSet(newborn));
        return tag;
    }
}
