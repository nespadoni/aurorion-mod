package com.aurorion.mundos.portal;

import com.aurorion.core.datapack.DatapackRegistry;
import com.aurorion.mundos.AurorionMundos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * As ligacoes dos datapacks, com um indice por dimensao de origem.
 *
 * <p>A leitura e do {@link DatapackRegistry} no {@code aurorion-core}; o que sobra aqui e o indice.
 * Ele existe porque a pergunta do caminho quente e sempre a mesma — "saindo desta dimensao, para
 * onde?" — e varrer a lista inteira para responde-la seria O(ligacoes) quando pode ser O(ligacoes
 * <b>desta</b> dimensao), que na pratica e uma ou duas.
 *
 * <p>O indice e reconstruido a cada carga e trocado de uma vez, igual ao resto do
 * {@code DatapackRegistry}: consulta em gameplay nunca copia nem sincroniza.
 */
public final class LinkCatalog {
    /**
     * Prefixo {@code aurorion/} de proposito, igual as linhas e as casas: num modpack pesado,
     * {@code data/<ns>/ligacoes/} tem chance real de colidir com outro mod.
     */
    public static final String DIRECTORY = "aurorion/ligacoes";

    private static final Comparator<DimensionLink> ORDER = Comparator
            .comparingInt(DimensionLink::order)
            .thenComparing(link -> link.id().toString());

    private static final DatapackRegistry<DimensionLink> REGISTRY =
            new DatapackRegistry<>(DIRECTORY, AurorionMundos.LOGGER, DimensionLink::codec, ORDER)
                    .onReload(LinkCatalog::index);

    private static volatile Map<ResourceKey<Level>, List<DimensionLink>> byOrigin = Map.of();

    private LinkCatalog() {
    }

    /** Um listener novo para o {@code AddReloadListenerEvent} — o vanilla pede um a cada reload. */
    public static PreparableReloadListener listener() {
        return REGISTRY.listener();
    }

    public static List<DimensionLink> all() {
        return REGISTRY.all();
    }

    public static List<ResourceLocation> ids() {
        return REGISTRY.ids();
    }

    /**
     * A ligacao que vale para um portal aceso em {@code origin}, na coluna {@code (x, z)}.
     *
     * <p>Circulo declarado ganha de ligacao sem area, independente da ordem no datapack: a ligacao
     * sem area e por definicao a generica, e deixar a ordem decidir isso faria a topologia depender
     * de um campo que existe para outra coisa.
     *
     * @return {@code null} quando esta dimensao nao tem ligacao nenhuma — e a resposta que devolve o
     *         controle ao vanilla, mantendo Nether e End exatamente como eram.
     */
    @Nullable
    public static DimensionLink route(ResourceKey<Level> origin, double x, double z) {
        return pick(byOrigin.get(origin), x, z);
    }

    /**
     * A escolha em si, separada do indice para poder ser exercitada sem servidor.
     *
     * <p>E a unica regra de verdade deste catalogo, e ela nao depende de estado nenhum — receber a
     * lista pronta e o que permite testa-la como funcao pura.
     */
    @Nullable
    static DimensionLink pick(@Nullable List<DimensionLink> candidates, double x, double z) {
        if (candidates == null) return null;

        DimensionLink generic = null;

        // Indice em vez de for-each: roda a cada travessia, e a lista tem uma ou duas entradas.
        for (int i = 0; i < candidates.size(); i++) {
            DimensionLink link = candidates.get(i);
            Optional<DimensionLink.Area> area = link.area();

            if (area.isEmpty()) {
                if (generic == null) {
                    generic = link;
                }
            } else if (area.get().contains(x, z)) {
                return link;
            }
        }

        return generic;
    }

    private static void index(List<DimensionLink> links) {
        Map<ResourceKey<Level>, List<DimensionLink>> built = new HashMap<>();

        for (DimensionLink link : links) {
            built.computeIfAbsent(link.from(), key -> new ArrayList<>()).add(link);
        }

        built.replaceAll((key, value) -> List.copyOf(value));
        byOrigin = Map.copyOf(built);
    }
}
