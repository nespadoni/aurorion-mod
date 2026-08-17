package com.aurorion.core.datapack;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Uma pasta de JSON de datapack lida para dentro de um catalogo consultavel.
 *
 * <p>Duas features do ecossistema ja tinham exatamente esta estrutura — as casas do
 * {@code aurorion-ato2} e as linhas do {@code aurorion-portais} — e cada ato novo traria mais uma.
 * Sempre a mesma coisa: ler a pasta, validar com um {@link Codec}, ignorar (com log) o arquivo
 * quebrado, ordenar, e publicar.
 *
 * <h2>Por que o estado nao mora no listener</h2>
 *
 * <p>O vanilla cria um listener novo a cada {@code /reload}, entao guardar o catalogo dentro dele
 * perderia tudo. Aqui a divisao e explicita: <b>o registro e permanente</b> (campo estatico do mod)
 * e <b>o listener e descartavel</b> ({@link #listener()} devolve um novo, que so escreve de volta
 * neste registro).
 *
 * <h2>Leitura sem custo</h2>
 *
 * <p>O estado sao dois snapshots imutaveis trocados de uma vez. Consulta em caminho de gameplay
 * nunca copia nem sincroniza; a troca so acontece na thread do servidor, durante o reload. Um
 * arquivo quebrado nao derruba os outros: ele e registrado no log e pulado (SDD §7.5 — dado que vem
 * de fora e hostil ate ser validado).
 *
 * <pre>{@code
 * private static final DatapackRegistry<House> HOUSES =
 *         new DatapackRegistry<>("aurorion/houses", LOGGER, House::codec, ORDER);
 *
 * // no AddReloadListenerEvent:
 * event.addListener(HOUSES.listener());
 * }</pre>
 *
 * @param <T> o tipo carregado. O {@code id} vem do caminho do arquivo, nunca do conteudo — igual a
 *            receita e loot table.
 */
public final class DatapackRegistry<T> {
    private static final Gson GSON = new Gson();

    private final String directory;
    private final Logger logger;
    private final Function<ResourceLocation, Codec<T>> codec;
    private final Comparator<T> order;

    @Nullable
    private Consumer<List<T>> afterReload;

    private volatile Map<ResourceLocation, T> byId = Map.of();
    private volatile List<T> sorted = List.of();
    private volatile List<ResourceLocation> sortedIds = List.of();

    /**
     * @param directory caminho dentro de {@code data/<namespace>/}. Prefixe com {@code aurorion/}:
     *                  num modpack pesado, {@code data/<ns>/casas/} tem chance real de colidir.
     * @param codec     recebe o id do arquivo, porque o id nao esta dentro do JSON.
     * @param order     ordem em que {@link #all()} devolve — normalmente um campo {@code order} do
     *                  proprio dado, com o id como desempate para a lista nao dancar entre reloads.
     */
    public DatapackRegistry(String directory, Logger logger,
                            Function<ResourceLocation, Codec<T>> codec, Comparator<T> order) {
        this.directory = directory;
        this.logger = logger;
        this.codec = codec;
        this.order = order;
    }

    /**
     * Roda depois de cada carga, ja com a lista ordenada. Serve para quem precisa reconstruir algo a
     * partir do catalogo — o {@code aurorion-portais} refaz o quadro de horarios aqui, para um
     * {@code /reload} que mude horario valer na hora.
     */
    public DatapackRegistry<T> onReload(Consumer<List<T>> hook) {
        this.afterReload = hook;
        return this;
    }

    /** Um listener novo, para entregar ao {@code AddReloadListenerEvent}. */
    public PreparableReloadListener listener() {
        return new SimpleJsonResourceReloadListener(GSON, directory) {
            @Override
            protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
                DatapackRegistry.this.accept(files);
            }
        };
    }

    // --- Consulta ------------------------------------------------------------------------------

    /** Tudo, ja na ordem. Nunca nula, pode ser vazia. */
    public List<T> all() {
        return sorted;
    }

    /** @return a entrada desse id, ou {@code null} se ela nao existe (id errado, ou datapack removido). */
    @Nullable
    public T get(ResourceLocation id) {
        return byId.get(id);
    }

    public boolean isEmpty() {
        return sorted.isEmpty();
    }

    /**
     * Os ids, na mesma ordem de {@link #all()} — pronto para sugestao de comando.
     *
     * <p>A lista e montada junto com a de valores, na carga, e nao derivada aqui: descobrir o id de
     * um valor exigiria procura-lo na lista ordenada, o que daria O(n²) numa consulta que o
     * autocomplete faz a cada tecla digitada.
     */
    public List<ResourceLocation> ids() {
        return sortedIds;
    }

    // --- Carga ---------------------------------------------------------------------------------

    private void accept(Map<ResourceLocation, JsonElement> files) {
        Map<ResourceLocation, T> parsed = new HashMap<>(files.size());

        files.forEach((id, json) -> codec.apply(id)
                .parse(JsonOps.INSTANCE, json)
                .resultOrPartial(error -> logger.error("'{}' ignorado em {}: {}", id, directory, error))
                .ifPresent(value -> parsed.put(id, value)));

        // Uma ordenacao so, sobre as entradas, e as duas listas saem dela — assim id e valor nunca
        // podem discordar de ordem, o que aconteceria se cada uma fosse ordenada por conta propria.
        List<Map.Entry<ResourceLocation, T>> entries = new ArrayList<>(parsed.entrySet());
        entries.sort(Comparator.comparing(Map.Entry::getValue, order));

        List<T> values = new ArrayList<>(entries.size());
        List<ResourceLocation> keys = new ArrayList<>(entries.size());
        for (Map.Entry<ResourceLocation, T> entry : entries) {
            keys.add(entry.getKey());
            values.add(entry.getValue());
        }

        byId = Map.copyOf(parsed);
        sorted = List.copyOf(values);
        sortedIds = List.copyOf(keys);

        logger.info("{}: {} entrada(s) carregada(s).", directory, sorted.size());

        Consumer<List<T>> hook = afterReload;
        if (hook != null) {
            hook.accept(sorted);
        }
    }
}
