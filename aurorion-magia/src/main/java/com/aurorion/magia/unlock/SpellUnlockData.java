package com.aurorion.magia.unlock;

import com.aurorion.core.data.PlayerMapNbt;
import com.aurorion.core.data.SavedDataAccess;
import com.aurorion.magia.AurorionMagia;
import com.aurorion.magia.unlock.SpellGrants.GrantKind;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Liberacoes por jogador, no {@code data/} do overworld.
 *
 * <p>SavedData e nao attachment no jogador: a staff libera escola para uma turma inteira com gente
 * offline, e o dado de jogador offline so existe no .dat dele. Aqui ele e um mapa em memoria.
 *
 * <p>Quem nunca foi liberado para nada <b>nao tem entrada</b>, como no {@code aurorion-vidas}: o
 * arquivo cresce com os alunos, nao com todo visitante que ja passou pelo servidor.
 */
public final class SpellUnlockData extends SavedData {
    private static final String FILE_ID = AurorionMagia.MOD_ID + "_unlocks";
    private static final String KEY_ENTRIES = "Entries";

    private static final SavedDataAccess<SpellUnlockData> ACCESS =
            new SavedDataAccess<>(FILE_ID, SpellUnlockData::new, SpellUnlockData::load);

    private final Map<UUID, SpellGrants> grants = new HashMap<>();

    public static SpellUnlockData get(MinecraftServer server) {
        return ACCESS.get(server);
    }

    private static SpellUnlockData load(CompoundTag tag, HolderLookup.Provider registries) {
        SpellUnlockData data = new SpellUnlockData();
        PlayerMapNbt.read(tag, KEY_ENTRIES, data.grants, SpellGrants::read);
        data.grants.values().removeIf(SpellGrants::isEmpty);
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put(KEY_ENTRIES, PlayerMapNbt.write(grants, (entry, value) -> value.write(entry)));
        return tag;
    }

    /** Nunca {@code null}; quem nao tem entrada recebe {@link SpellGrants#EMPTY}, que nao aloca. */
    public SpellGrants grantsOf(UUID player) {
        return grants.getOrDefault(player, SpellGrants.EMPTY);
    }

    public boolean grant(UUID player, GrantKind kind, ResourceLocation id) {
        boolean changed = grants.computeIfAbsent(player, ignored -> new SpellGrants()).grant(kind, id);
        if (changed) setDirty();
        return changed;
    }

    public boolean revoke(UUID player, GrantKind kind, ResourceLocation id) {
        SpellGrants current = grants.get(player);
        if (current == null || !current.revoke(kind, id)) return false;
        if (current.isEmpty()) grants.remove(player);
        setDirty();
        return true;
    }

    /** Personagem novo comeca sem aula nenhuma. */
    public void clear(UUID player) {
        if (grants.remove(player) != null) setDirty();
    }
}
