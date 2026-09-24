package com.aurorion.essentials.server;

import com.aurorion.core.data.PlayerMapNbt;
import com.aurorion.core.data.SavedDataAccess;
import com.aurorion.essentials.AurorionEssentials;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Nomes falsos persistidos entre reinicios do servidor, guardados como o texto cru digitado no
 * comando (com os codigos "&"). O {@link com.aurorion.essentials.fakename.FakeName} colorido e
 * sempre reconstruido em runtime a partir disso — nada de Component serializado em disco.
 */
public class FakeNameData extends SavedData {
    private static final String FILE_ID = AurorionEssentials.MOD_ID + "_fake_names";
    private static final String KEY_ENTRIES = "Entries";
    private static final String KEY_NAME = "Name";

    private static final SavedDataAccess<FakeNameData> ACCESS =
            new SavedDataAccess<>(FILE_ID, FakeNameData::new, FakeNameData::load);

    private final Map<UUID, String> names = new HashMap<>();

    public static FakeNameData get(MinecraftServer server) {
        return ACCESS.get(server);
    }

    private static FakeNameData load(CompoundTag tag, HolderLookup.Provider registries) {
        FakeNameData data = new FakeNameData();
        PlayerMapNbt.read(tag, KEY_ENTRIES, data.names, entry -> entry.getString(KEY_NAME));
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put(KEY_ENTRIES, PlayerMapNbt.write(names, (entry, name) -> entry.putString(KEY_NAME, name)));
        return tag;
    }

    @Nullable
    public String getRaw(UUID player) {
        return names.get(player);
    }

    /**
     * Todos os nomes gravados, inclusive de quem esta offline — o {@code FakeNameRegistry} so tem
     * quem esta online agora. E o que permite ao {@code /deathhistory} achar a conta de alguem pelo
     * nome de personagem dias depois da morte.
     *
     * <p>Copia imutavel: quem consulta nao mexe no estado salvo, e a copia sai barata (um nome por
     * jogador que ja usou o comando).
     */
    public Map<UUID, String> allRaw() {
        return Map.copyOf(names);
    }

    /** @return true se algo mudou de fato. */
    public boolean setRaw(UUID player, @Nullable String raw) {
        String previous = raw == null ? names.remove(player) : names.put(player, raw);
        boolean changed = !Objects.equals(previous, raw);

        if (changed) setDirty();
        return changed;
    }
}
