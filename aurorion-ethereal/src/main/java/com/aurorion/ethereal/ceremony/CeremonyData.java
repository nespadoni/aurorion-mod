package com.aurorion.ethereal.ceremony;

import com.aurorion.core.data.PlayerMapNbt;
import com.aurorion.core.data.SavedDataAccess;
import com.aurorion.ethereal.AurorionEthereal;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Compatibilidade com filas salvas por versoes antigas.
 * Novas cerimonias nao sao agendadas; a staff pode cancelar/limpar registros legados.
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

    public boolean cancel(UUID player) {
        if (rites.remove(player) == null) return false;
        setDirty();
        return true;
    }
}
