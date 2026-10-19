package com.aurorion.magia.passive;

import com.aurorion.core.data.PlayerMapNbt;
import com.aurorion.core.data.SavedDataAccess;
import com.aurorion.magia.AurorionMagia;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * O estado das passivas dos representantes que precisa sobreviver a morte, ao relog e ao restart: a
 * energia quantica do Robs e o "uma vez por dia" da Gretta.
 *
 * <p>SavedData, e nao NBT de jogador, pelo mesmo motivo do {@link PassiveData}: o NBT persistente do
 * jogador nao e copiado na morte, e quem morresse com a energia zerada voltaria cheio. Uma entrada so
 * para quem ja usou alguma — o arquivo cresce com os representantes, nao com o servidor.
 *
 * <p>Os relogios sao <b>calculados, nao contados</b>: guarda-se o instante da ultima mudanca e o valor
 * naquele instante, e o valor de agora sai de uma conta na hora em que alguem pergunta. Ninguem tica
 * energia de ninguem.
 */
public final class RepresentativeData extends SavedData {
    private static final String KEY_ENTRIES = "Entries";

    private static final SavedDataAccess<RepresentativeData> ACCESS = new SavedDataAccess<>(
            AurorionMagia.MOD_ID + "_representantes", RepresentativeData::new, RepresentativeData::load);

    private final Map<UUID, CompoundTag> entries = new HashMap<>();

    public static RepresentativeData get(MinecraftServer server) {
        return ACCESS.get(server);
    }

    /** O registro de alguem, criado vazio na primeira vez. Quem escreve nele chama {@link #setDirty()}. */
    public CompoundTag of(UUID player) {
        return entries.computeIfAbsent(player, ignored -> new CompoundTag());
    }

    /** Personagem novo comeca descansado. */
    public void clear(UUID player) {
        if (entries.remove(player) != null) setDirty();
    }

    private static RepresentativeData load(CompoundTag tag, HolderLookup.Provider registries) {
        RepresentativeData data = new RepresentativeData();
        PlayerMapNbt.read(tag, KEY_ENTRIES, data.entries, entry -> {
            CompoundTag copy = entry.copy();
            copy.remove(PlayerMapNbt.KEY_PLAYER);
            return copy;
        });
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put(KEY_ENTRIES, PlayerMapNbt.write(entries, (entry, value) -> {
            for (String key : value.getAllKeys()) {
                if (!PlayerMapNbt.KEY_PLAYER.equals(key)) entry.put(key, value.get(key).copy());
            }
        }));
        return tag;
    }
}
