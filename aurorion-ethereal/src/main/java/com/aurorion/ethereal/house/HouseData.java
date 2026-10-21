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
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
 *
 * <p><b>Casa oculta.</b> A staff cadastra a casa antes da cerimonia (o Teste das Casas decide antes
 * da revelacao), e ate o rito o cadastro e informacao da equipe. Quem esta em {@code hidden} tem
 * casa gravada, mas para o resto do jogo (tab, mural, areas, placar, {@code /casa}) ainda nao tem
 * nenhuma. Grava-se o conjunto dos <em>ocultos</em>, e nao o dos revelados, de proposito: um save
 * anterior a esta regra nao tem a lista, e quem ja tinha casa continua exatamente como estava.
 */
public class HouseData extends SavedData {
    private static final String FILE_ID = AurorionEthereal.MOD_ID + "_houses";
    private static final String KEY_ENTRIES = "Entries";
    private static final String KEY_HOUSE = "House";
    private static final String KEY_HIDDEN = "Hidden";

    private static final SavedDataAccess<HouseData> ACCESS =
            new SavedDataAccess<>(FILE_ID, HouseData::new, HouseData::load);

    private final Map<UUID, ResourceLocation> byPlayer = new HashMap<>();
    private final Map<ResourceLocation, Integer> counts = new HashMap<>();
    private final Set<UUID> hidden = new HashSet<>();

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
        PlayerMapNbt.readSet(tag, KEY_HIDDEN, data.hidden);
        // Oculto sem casa nao significa nada; descartar evita que um cadastro futuro herde a marca.
        data.hidden.retainAll(data.byPlayer.keySet());
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put(KEY_ENTRIES, PlayerMapNbt.write(byPlayer,
                (entry, house) -> entry.putString(KEY_HOUSE, house.toString())));
        tag.put(KEY_HIDDEN, PlayerMapNbt.writeSet(hidden));
        return tag;
    }

    /** A casa cadastrada, revelada ou nao. So para regra de servidor e ferramenta de staff. */
    @Nullable
    public ResourceLocation houseOf(UUID player) {
        return byPlayer.get(player);
    }

    /** A casa que o jogo pode mostrar: {@code null} enquanto o cadastro ainda nao foi revelado. */
    @Nullable
    public ResourceLocation publicHouseOf(UUID player) {
        return hidden.contains(player) ? null : byPlayer.get(player);
    }

    public boolean isHidden(UUID player) {
        return hidden.contains(player);
    }

    /**
     * Marca ou desmarca o cadastro como oculto. Sem casa nao ha o que ocultar.
     *
     * @return true se algo mudou de fato.
     */
    public boolean setHidden(UUID player, boolean value) {
        boolean changed = value
                ? byPlayer.containsKey(player) && hidden.add(player)
                : hidden.remove(player);
        if (changed) setDirty();
        return changed;
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
        if (house == null) hidden.remove(player);
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
