package com.aurorion.ato2.house;

import com.aurorion.ato2.AurorionAto2;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Le as casas dos datapacks a cada carga de mundo e a cada {@code /reload}.
 *
 * <p>O catalogo e <b>autoridade do servidor</b>: o cliente nunca le esses arquivos, ele so recebe a
 * lista pronta quando abre o altar. Isso mantem a regra do SDD §7.5 (tudo que vem da rede e hostil
 * ate ser validado aqui) — se um cliente mandar um id de casa que nao existe neste mapa, a escolha
 * e recusada.
 *
 * <p>O estado fica em dois snapshots imutaveis trocados de uma vez. Leitura no caminho de gameplay
 * nunca copia nada nem sincroniza; a troca so acontece na thread do servidor, durante o reload.
 */
public class HouseCatalog extends SimpleJsonResourceReloadListener {
    /**
     * Prefixo {@code aurorion/} de proposito: num modpack pesado, {@code data/<ns>/houses/} tem
     * chance real de colidir com outro mod que tambem tenha "casas".
     */
    public static final String DIRECTORY = "aurorion/houses";

    private static final Gson GSON = new Gson();

    private static final Comparator<House> ORDER = Comparator
            .comparingInt(House::order)
            .thenComparing(house -> house.id().toString());

    private static volatile Map<ResourceLocation, House> byId = Map.of();
    private static volatile List<House> sorted = List.of();

    public HouseCatalog() {
        super(GSON, DIRECTORY);
    }

    /** Todas as casas, ja na ordem em que devem aparecer na GUI. Nunca nulo, pode ser vazio. */
    public static List<House> all() {
        return sorted;
    }

    /** @return a casa desse id, ou {@code null} se ela nao existe (id desconhecido ou datapack removido). */
    @Nullable
    public static House get(ResourceLocation id) {
        return byId.get(id);
    }

    public static boolean isEmpty() {
        return sorted.isEmpty();
    }

    public static List<ResourceLocation> ids() {
        List<ResourceLocation> ids = new ArrayList<>(sorted.size());
        for (House house : sorted) {
            ids.add(house.id());
        }
        return ids;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<ResourceLocation, House> parsed = new HashMap<>(files.size());

        files.forEach((id, json) -> House.codec(id)
                .parse(JsonOps.INSTANCE, json)
                .resultOrPartial(error -> AurorionAto2.LOGGER.error("Casa '{}' ignorada: {}", id, error))
                .ifPresent(house -> parsed.put(id, house)));

        List<House> ordered = new ArrayList<>(parsed.values());
        ordered.sort(ORDER);

        byId = Map.copyOf(parsed);
        sorted = List.copyOf(ordered);

        AurorionAto2.LOGGER.info("Catalogo de casas carregado: {} casa(s).", sorted.size());
    }
}
