package com.aurorion.magia.passive;

import com.aurorion.core.data.PlayerMapNbt;
import com.aurorion.core.data.SavedDataAccess;
import com.aurorion.magia.AurorionMagia;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * As passivas de cada personagem, no {@code data/} do overworld.
 *
 * <p>Mesmas duas escolhas do {@code SpellUnlockData}, pelos mesmos motivos: <b>SavedData e nao
 * attachment</b> (a staff concede a quem esta offline, e o dado de jogador offline so existe no .dat
 * dele) e <b>sem entrada para quem nao tem nada</b> (o arquivo cresce com quem recebeu passiva, nao
 * com todo visitante que ja passou pelo servidor).
 *
 * <p>Sao dois conjuntos por pessoa: o que ela <b>tem</b> e o que esta <b>ligado</b>. A distincao so
 * importa para as passivas com interruptor ({@link Passive#isToggleable()}); para as outras, ter e
 * usar sao a mesma coisa, e o conjunto de ligadas nem e consultado.
 *
 * <p>Morte definitiva zera tudo, como a aula de magia: {@code CharacterResetEvent} chama
 * {@link #clear(UUID)}. Personagem novo nao herda a marca do anterior.
 */
public final class PassiveData extends SavedData {
    private static final String FILE_ID = AurorionMagia.MOD_ID + "_passives";
    private static final String KEY_ENTRIES = "Entries";
    private static final String KEY_OWNED = "Owned";
    private static final String KEY_ACTIVE = "Active";

    private static final SavedDataAccess<PassiveData> ACCESS =
            new SavedDataAccess<>(FILE_ID, PassiveData::new, PassiveData::load);

    private final Map<UUID, Entry> entries = new HashMap<>();

    public static PassiveData get(MinecraftServer server) {
        return ACCESS.get(server);
    }

    private static PassiveData load(CompoundTag tag, HolderLookup.Provider registries) {
        PassiveData data = new PassiveData();
        PlayerMapNbt.read(tag, KEY_ENTRIES, data.entries, Entry::read);
        data.entries.values().removeIf(Entry::isEmpty);
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put(KEY_ENTRIES, PlayerMapNbt.write(entries, (entry, value) -> value.write(entry)));
        return tag;
    }

    public boolean has(UUID player, Passive passive) {
        Entry entry = entries.get(player);
        return entry != null && entry.owned.contains(passive);
    }

    /** "Esta valendo agora?" — tem a passiva e, se ela tiver interruptor, esta ligada. */
    public boolean isActive(UUID player, Passive passive) {
        Entry entry = entries.get(player);
        if (entry == null || !entry.owned.contains(passive)) return false;
        return !passive.isToggleable() || entry.active.contains(passive);
    }

    public Set<Passive> owned(UUID player) {
        Entry entry = entries.get(player);
        return entry == null ? EnumSet.noneOf(Passive.class) : EnumSet.copyOf(entry.owned);
    }

    /** @return se mudou alguma coisa; {@code false} quando a pessoa ja tinha a passiva. */
    public boolean grant(UUID player, Passive passive) {
        Entry entry = entries.computeIfAbsent(player, ignored -> new Entry());
        if (!entry.owned.add(passive)) return false;
        setDirty();
        return true;
    }

    public boolean revoke(UUID player, Passive passive) {
        Entry entry = entries.get(player);
        if (entry == null || !entry.owned.remove(passive)) return false;
        entry.active.remove(passive);
        if (entry.isEmpty()) entries.remove(player);
        setDirty();
        return true;
    }

    /** @return se mudou alguma coisa; {@code false} quando ja estava no estado pedido. */
    public boolean setActive(UUID player, Passive passive, boolean on) {
        Entry entry = entries.get(player);
        if (entry == null || !entry.owned.contains(passive)) return false;
        boolean changed = on ? entry.active.add(passive) : entry.active.remove(passive);
        if (changed) setDirty();
        return changed;
    }

    /** Personagem novo comeca sem marca nenhuma. */
    public void clear(UUID player) {
        if (entries.remove(player) != null) setDirty();
    }

    private static final class Entry {
        private final Set<Passive> owned = EnumSet.noneOf(Passive.class);
        private final Set<Passive> active = EnumSet.noneOf(Passive.class);

        boolean isEmpty() {
            return owned.isEmpty();
        }

        void write(CompoundTag tag) {
            tag.put(KEY_OWNED, writeSet(owned));
            tag.put(KEY_ACTIVE, writeSet(active));
        }

        static Entry read(CompoundTag tag) {
            Entry entry = new Entry();
            readSet(tag, KEY_OWNED, entry.owned);
            readSet(tag, KEY_ACTIVE, entry.active);
            // "Ligada" sem "tem" e lixo de uma passiva revogada por comando antigo: nao vale nada.
            entry.active.retainAll(entry.owned);
            return entry;
        }

        private static ListTag writeSet(Set<Passive> set) {
            ListTag list = new ListTag();
            for (Passive passive : set) list.add(StringTag.valueOf(passive.id().toString()));
            return list;
        }

        /**
         * Id que nao existe mais no codigo e <b>ignorado, nao apagado</b>? Nao: aqui ele e ignorado e
         * some no proximo save, porque {@link Passive} e enum e nao ha como guardar o que nao existe.
         * E a diferenca aceita em relacao ao {@code SpellGrants}, que guarda ids crus porque ali eles
         * vem de outros mods; passiva e sempre nossa.
         */
        private static void readSet(CompoundTag tag, String key, Set<Passive> out) {
            ListTag list = tag.getList(key, Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) {
                String id = list.getString(i);
                for (Passive passive : Passive.values()) {
                    if (passive.id().toString().equals(id)) out.add(passive);
                }
            }
        }
    }
}
