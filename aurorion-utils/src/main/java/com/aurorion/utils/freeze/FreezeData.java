package com.aurorion.utils.freeze;

import com.aurorion.core.data.PlayerMapNbt;
import com.aurorion.core.data.SavedDataAccess;
import com.aurorion.utils.AurorionUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Quem a staff congelou <b>sem prazo</b>. O efeito sozinho ja sobrevive a relog (vai no save do
 * jogador), mas morrer limpa todo efeito — e quem foi congelado por comando continua congelado ate
 * alguem dar {@code /unfreeze}, inclusive depois de renascer.
 *
 * <p>Freeze com tempo (comando com segundos, ou magia) nao entra aqui: acabou, acabou.
 */
public final class FreezeData extends SavedData {
    private static final String FILE_ID = AurorionUtils.MOD_ID + "_freeze";
    private static final String KEY_PLAYERS = "Players";

    private static final SavedDataAccess<FreezeData> ACCESS =
            new SavedDataAccess<>(FILE_ID, FreezeData::new, FreezeData::load);

    private final Set<UUID> frozen = new HashSet<>();

    public static FreezeData get(MinecraftServer server) {
        return ACCESS.get(server);
    }

    private static FreezeData load(CompoundTag tag, HolderLookup.Provider registries) {
        FreezeData data = new FreezeData();
        PlayerMapNbt.readSet(tag, KEY_PLAYERS, data.frozen);
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put(KEY_PLAYERS, PlayerMapNbt.writeSet(frozen));
        return tag;
    }

    public boolean contains(UUID player) {
        return frozen.contains(player);
    }

    public void add(UUID player) {
        if (frozen.add(player)) setDirty();
    }

    public void remove(UUID player) {
        if (frozen.remove(player)) setDirty();
    }
}
