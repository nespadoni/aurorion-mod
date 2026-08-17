package com.aurorion.ato2.house;

import com.aurorion.ato2.AurorionAto2;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Quem esta em qual casa, persistido no {@code data/} do mundo (overworld — vale para o servidor
 * inteiro, nao por dimensao). E o registro por UUID e nao por entidade de jogador de proposito:
 * continua respondendo com o jogador offline, que e o que faz {@code /casa ver} e
 * {@code /casa definir} servirem como ferramenta de moderacao.
 *
 * <p>A contagem por casa e mantida incrementalmente em vez de recontada a cada consulta: ela e lida
 * na abertura de cada altar (para mostrar lotacao) e na validacao de cada escolha.
 */
public class HouseData extends SavedData {
    private static final String FILE_ID = AurorionAto2.MOD_ID + "_houses";
    private static final String KEY_ENTRIES = "Entries";
    private static final String KEY_PLAYER = "Player";
    private static final String KEY_HOUSE = "House";

    private final Map<UUID, ResourceLocation> byPlayer = new HashMap<>();
    private final Map<ResourceLocation, Integer> counts = new HashMap<>();

    public static HouseData get(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) throw new IllegalStateException("Overworld ainda nao carregado");

        return overworld.getDataStorage().computeIfAbsent(
                new Factory<>(HouseData::new, HouseData::load),
                FILE_ID
        );
    }

    private static HouseData load(CompoundTag tag, HolderLookup.Provider registries) {
        HouseData data = new HouseData();
        ListTag entries = tag.getList(KEY_ENTRIES, Tag.TAG_COMPOUND);

        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            if (!entry.hasUUID(KEY_PLAYER)) continue;

            ResourceLocation house = ResourceLocation.tryParse(entry.getString(KEY_HOUSE));
            if (house == null) continue;

            // Casa que sumiu do datapack continua gravada: o dado do jogador nao e destruido so
            // porque alguem estava editando o JSON. Quem trata "casa desconhecida" e a leitura.
            data.byPlayer.put(entry.getUUID(KEY_PLAYER), house);
            data.counts.merge(house, 1, Integer::sum);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag entries = new ListTag();

        byPlayer.forEach((player, house) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID(KEY_PLAYER, player);
            entry.putString(KEY_HOUSE, house.toString());
            entries.add(entry);
        });

        tag.put(KEY_ENTRIES, entries);
        return tag;
    }

    @Nullable
    public ResourceLocation houseOf(UUID player) {
        return byPlayer.get(player);
    }

    public int membersOf(ResourceLocation house) {
        return counts.getOrDefault(house, 0);
    }

    /**
     * @param house casa nova, ou {@code null} para tirar o jogador da casa atual.
     * @return true se algo mudou de fato.
     */
    public boolean setHouse(UUID player, @Nullable ResourceLocation house) {
        ResourceLocation previous = house == null ? byPlayer.remove(player) : byPlayer.put(player, house);
        if (Objects.equals(previous, house)) {
            return false;
        }

        if (previous != null) {
            counts.merge(previous, -1, (current, delta) -> current + delta <= 0 ? null : current + delta);
        }
        if (house != null) {
            counts.merge(house, 1, Integer::sum);
        }

        setDirty();
        return true;
    }
}
