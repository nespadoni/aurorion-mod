package com.aurorion.core.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * Acesso a um {@link SavedData} do overworld, com cache.
 *
 * <p>Resolve tres problemas que apareciam repetidos em todo mod do ecossistema que persiste algo
 * por jogador:
 *
 * <ul>
 *   <li><b>Repeticao</b>: o par "pega o overworld, chama {@code computeIfAbsent}" estava escrito
 *       identico em quatro mods.</li>
 *   <li><b>Alocacao por chamada</b>: {@code computeIfAbsent} exige um {@link SavedData.Factory}, e
 *       montar um novo a cada consulta aloca — o que importa de verdade quando a consulta acontece
 *       em caminho de tick. Aqui a fabrica e construida <b>uma vez</b>, no construtor.</li>
 *   <li><b>Vazamento entre mundos</b>: ver abaixo.</li>
 * </ul>
 *
 * <h2>Por que a invalidacao nao e responsabilidade de quem usa</h2>
 *
 * <p>No servidor integrado, abrir um mundo depois de outro reutiliza o mesmo processo. Uma instancia
 * guardada de um mundo anterior faria o mundo novo ler os dados do antigo — nomes, casas, vidas e
 * passes de outra partida.
 *
 * <p>A primeira versao desta classe pedia que cada mod chamasse {@link #invalidate()} num listener de
 * {@code ServerStoppedEvent}. Isso <b>mudou o bug de lugar em vez de remove-lo</b>: dois mods do
 * ecossistema nao tinham esse listener e passaram a precisar de um. Uma abstracao que exige
 * disciplina de quem chama nao e uma abstracao.
 *
 * <p>Agora sao duas travas, e nenhuma depende de quem usa:
 *
 * <ol>
 *   <li>Toda instancia se registra numa lista, e o proprio {@code aurorion-core} zera todas quando o
 *       servidor para ({@code CoreServerEvents}).</li>
 *   <li>O cache guarda <b>qual</b> servidor o produziu. Se chegar outro, e descartado na hora —
 *       entao mesmo que o evento nao dispare, o dado nunca cruza de um mundo para outro.</li>
 * </ol>
 *
 * <p>Uso tipico, como campo estatico da propria classe de dados:
 *
 * <pre>{@code
 * private static final SavedDataAccess<PassData> ACCESS =
 *         new SavedDataAccess<>("aurorion_portais_passes", PassData::new, PassData::load);
 *
 * public static PassData get(MinecraftServer server) {
 *     return ACCESS.get(server);
 * }
 * }</pre>
 *
 * <p>O overworld e o dono do arquivo de proposito: os dados sao do servidor inteiro, nao de uma
 * dimensao. Guardar por dimensao faria a vida de um jogador mudar conforme onde ele esta.
 *
 * <p><b>Thread</b>: use so na thread do servidor. O cache nao e sincronizado porque {@code SavedData}
 * do vanilla tambem nao e — quem ler isto de outra thread ja tem um problema maior.
 */
public final class SavedDataAccess<T extends SavedData> {
    /**
     * Toda instancia criada. Sao poucas e vivem para sempre (campos estaticos dos mods), entao a
     * lista nao cresce sem limite. {@code CopyOnWriteArrayList} porque a escrita acontece na carga
     * das classes e a leitura no desligamento do servidor.
     */
    private static final List<SavedDataAccess<?>> ALL = new CopyOnWriteArrayList<>();

    private final String fileId;
    private final SavedData.Factory<T> factory;

    @Nullable
    private T cached;
    /**
     * De qual servidor veio o {@link #cached}. Comparado por identidade: cada mundo aberto no
     * servidor integrado e um {@link MinecraftServer} novo.
     */
    @Nullable
    private MinecraftServer cachedFrom;

    /**
     * @param fileId nome do arquivo em {@code data/} do mundo, sem extensao. Use o {@code mod_id}
     *               como prefixo para nao colidir com outro mod do modpack.
     */
    public SavedDataAccess(String fileId, Supplier<T> creator,
                           BiFunction<CompoundTag, HolderLookup.Provider, T> loader) {
        this.fileId = fileId;
        this.factory = new SavedData.Factory<>(creator, loader);
        ALL.add(this);
    }

    public T get(MinecraftServer server) {
        T current = cached;
        if (current != null && cachedFrom == server) {
            return current;
        }

        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            throw new IllegalStateException("Overworld ainda nao carregado ao pedir '" + fileId + "'");
        }

        current = overworld.getDataStorage().computeIfAbsent(factory, fileId);
        cached = current;
        cachedFrom = server;
        return current;
    }

    /**
     * Solta a instancia guardada. Nao e preciso chamar: o {@code aurorion-core} ja faz isso ao parar
     * o servidor. Continua publico para quem tiver um caso fora do comum.
     */
    public void invalidate() {
        cached = null;
        cachedFrom = null;
    }

    /**
     * Zera todos os caches. Chamado pelo {@code aurorion-core} em {@code ServerStoppedEvent} — e o
     * que solta a referencia ao {@link MinecraftServer} parado, que de outro jeito seguraria o grafo
     * inteiro do servidor na memoria ate o proximo mundo abrir.
     */
    public static void invalidateAll() {
        for (SavedDataAccess<?> access : ALL) {
            access.invalidate();
        }
    }
}
