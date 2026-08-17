package com.aurorion.ato2.house;

import com.aurorion.ato2.AurorionAto2;
import com.aurorion.core.datapack.DatapackRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;

/**
 * As casas dos datapacks, recarregadas na carga do mundo e a cada {@code /reload}.
 *
 * <p>O catalogo e <b>autoridade do servidor</b>: o cliente nunca le esses arquivos, ele so recebe a
 * lista pronta quando abre o altar. Se um cliente mandar um id de casa que nao existe aqui, a
 * escolha e recusada (SDD §7.5).
 *
 * <p>A mecanica de ler pasta, validar por {@link com.mojang.serialization.Codec}, ignorar arquivo
 * quebrado e publicar snapshot imutavel e do {@link DatapackRegistry}, no {@code aurorion-core} —
 * era identica a das linhas do {@code aurorion-portais}, e cada ato novo traria mais uma copia.
 */
public final class HouseCatalog {
    /**
     * Prefixo {@code aurorion/} de proposito: num modpack pesado, {@code data/<ns>/houses/} tem
     * chance real de colidir com outro mod que tambem tenha "casas".
     */
    public static final String DIRECTORY = "aurorion/houses";

    private static final Comparator<House> ORDER = Comparator
            .comparingInt(House::order)
            .thenComparing(house -> house.id().toString());

    private static final DatapackRegistry<House> REGISTRY =
            new DatapackRegistry<>(DIRECTORY, AurorionAto2.LOGGER, House::codec, ORDER);

    private HouseCatalog() {
    }

    /** Um listener novo para o {@code AddReloadListenerEvent} — o vanilla pede um a cada reload. */
    public static PreparableReloadListener listener() {
        return REGISTRY.listener();
    }

    /** Todas as casas, ja na ordem em que devem aparecer na GUI. Nunca nulo, pode ser vazio. */
    public static List<House> all() {
        return REGISTRY.all();
    }

    /** @return a casa desse id, ou {@code null} se ela nao existe (id desconhecido ou datapack removido). */
    @Nullable
    public static House get(ResourceLocation id) {
        return REGISTRY.get(id);
    }

    public static boolean isEmpty() {
        return REGISTRY.isEmpty();
    }

    public static List<ResourceLocation> ids() {
        return REGISTRY.ids();
    }
}
