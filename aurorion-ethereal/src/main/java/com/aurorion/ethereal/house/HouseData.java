package com.aurorion.ethereal.house;

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
import java.util.Objects;
import java.util.UUID;

/**
 * Quem esta em qual casa, persistido no {@code data/} do mundo (overworld — vale para o servidor
 * inteiro, nao por dimensao). E o registro por UUID e nao por entidade de jogador de proposito:
 * continua respondendo com o jogador offline, que e o que faz {@code /casa ver}, {@code /casa
 * definir} e a confirmacao de cerimonia servirem como ferramenta de moderacao a qualquer hora.
 *
 * <p>A contagem por casa e mantida incrementalmente em vez de recontada a cada consulta: ela e lida
 * na abertura de cada altar (para mostrar lotacao), na validacao de cada vinculacao e no calculo do
 * total de pontos de cada casa para o placar.
 */
public class HouseData extends SavedData {
    private static final String FILE_ID = AurorionEthereal.MOD_ID + "_houses";
    private static final String KEY_ENTRIES = "Entries";
    private static final String KEY_HOUSE = "House";

    private static final SavedDataAccess<HouseData> ACCESS =
            new SavedDataAccess<>(FILE_ID, HouseData::new, HouseData::load);

    private final Map<UUID, ResourceLocation> byPlayer = new HashMap<>();
    private final Map<ResourceLocation, Integer> counts = new HashMap<>();

    public static HouseData get(MinecraftServer server) {
        return ACCESS.get(server);
    }

    private static HouseData load(CompoundTag tag, HolderLookup.Provider registries) {
        HouseData data = new HouseData();

        PlayerMapNbt.read(tag, KEY_ENTRIES, data.byPlayer, entry -> {
            // Casa que sumiu do datapack continua gravada: o dado do jogador nao e destruido so
            // porque alguem estava editando o JSON. Quem trata "casa desconhecida" e a leitura.
            // Ja um id que nem parseia mais e descartado (null), e a entrada some.
            ResourceLocation house = ResourceLocation.tryParse(entry.getString(KEY_HOUSE));
            if (house != null) {
                data.counts.merge(house, 1, Integer::sum);
            }
            return house;
        });
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put(KEY_ENTRIES, PlayerMapNbt.write(byPlayer,
                (entry, house) -> entry.putString(KEY_HOUSE, house.toString())));
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
