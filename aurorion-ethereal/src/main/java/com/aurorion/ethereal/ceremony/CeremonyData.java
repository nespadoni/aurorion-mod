package com.aurorion.ethereal.ceremony;

import com.aurorion.core.data.PlayerMapNbt;
import com.aurorion.core.data.SavedDataAccess;
import com.aurorion.ethereal.AurorionEthereal;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Ritos que ficaram esperando o dono voltar.
 *
 * <p>A staff define a casa de alguem a qualquer hora, inclusive com a pessoa offline — num servidor
 * de 80 jogadores esse e o caso comum, nao o raro. O que nao pode acontecer e a cerimonia dela
 * terminar num anuncio de chat que ela nunca viu: o rito espera aqui e toca no proximo login.
 *
 * <p>Guarda o <b>id da casa</b>, e nao a casa: entre gravar e tocar cabe um {@code /reload} que muda
 * cor, lema ou nome. O rito precisa mostrar o que a casa e <em>na hora de tocar</em>.
 */
public final class CeremonyData extends SavedData {
    private static final String FILE_ID = AurorionEthereal.MOD_ID + "_ceremonies";
    private static final String KEY_RITES = "PendingRites";
    private static final String KEY_HOUSE = "House";

    private static final SavedDataAccess<CeremonyData> ACCESS =
            new SavedDataAccess<>(FILE_ID, CeremonyData::new, CeremonyData::load);

    private final Map<UUID, ResourceLocation> rites = new HashMap<>();

    public static CeremonyData get(MinecraftServer server) {
        return ACCESS.get(server);
    }

    private static CeremonyData load(CompoundTag tag, HolderLookup.Provider registries) {
        CeremonyData data = new CeremonyData();

        PlayerMapNbt.read(tag, KEY_RITES, data.rites,
                entry -> ResourceLocation.tryParse(entry.getString(KEY_HOUSE)));
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put(KEY_RITES, PlayerMapNbt.write(rites,
                (entry, house) -> entry.putString(KEY_HOUSE, house.toString())));
        return tag;
    }

    public void queue(UUID player, ResourceLocation house) {
        rites.put(player, house);
        setDirty();
    }

    /** Tira o rito da fila e devolve. Chamada uma vez, no login. */
    @Nullable
    public ResourceLocation take(UUID player) {
        ResourceLocation house = rites.remove(player);
        if (house != null) setDirty();
        return house;
    }

    public boolean cancel(UUID player) {
        if (rites.remove(player) == null) return false;
        setDirty();
        return true;
    }
}
