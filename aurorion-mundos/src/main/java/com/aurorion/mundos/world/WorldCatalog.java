package com.aurorion.mundos.world;

import com.aurorion.core.config.DerivedConfig;
import com.aurorion.mundos.AurorionMundos;
import com.aurorion.mundos.config.MundosConfig;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Quais dimensoes sao <b>deste mod</b>, e com que seed cada uma gera.
 *
 * <p>A resposta vem da config, e a lista de seeds e ao mesmo tempo a lista de mundos: <b>ter seed
 * declarado e o que torna uma dimensao nossa</b>. Nao ha lista separada de "dimensoes gerenciadas"
 * porque duas listas que precisam concordar sao duas chances de discordarem.
 *
 * <p>A consequencia que importa num modpack pesado e a inversa: dimensao <b>sem</b> seed declarado
 * nao e tocada por nada deste mod — nem o terreno, nem a barreira. Uma dimensao de mod de terceiro
 * nunca muda de comportamento so porque este mod esta instalado (SDD §2, nunca interferir fora do
 * escopo proprio).
 *
 * <h2>Por que config e nao datapack</h2>
 *
 * <p>Ver {@link MundosConfig}: um {@code /reload} nao pode mudar seed de mundo ja gerado. Config e
 * lida uma vez, no boot, antes de {@code createLevels} — que e exatamente a janela em que este dado
 * e consultado pela primeira vez.
 *
 * <h2>Custo</h2>
 *
 * <p>{@link #seedFor} e chamado de {@code ServerLevel#getSeed()}, que o vanilla consulta uma vez por
 * nivel criado, uma vez por chunk gerado (decoracao) e na troca de dimensao. Nunca por tick. O
 * {@link DerivedConfig} do core garante que o caso normal (config nao mudou) custa uma comparacao de
 * referencia, sem reparsear nada.
 */
public final class WorldCatalog {
    private static final DerivedConfig<List<? extends String>, Map<ResourceKey<Level>, Long>> SEEDS =
            new DerivedConfig<>(WorldCatalog::rawSeeds, WorldCatalog::parseSeeds);

    private static final DerivedConfig<List<? extends String>, Set<ResourceKey<Level>>> RUNTIME_GENERATION =
            new DerivedConfig<>(WorldCatalog::rawRuntimeGeneration, WorldCatalog::parseDimensions);

    /**
     * Config ainda nao carregada e um estado real e transitorio (classes carregam antes do servidor
     * subir). Avisar uma vez basta; avisar sempre encheria o log de quem so abriu o menu principal.
     */
    private static boolean warnedNotLoaded;

    private WorldCatalog() {
    }

    /**
     * O seed declarado para esta dimensao, ou {@code null} se ela nao e nossa.
     *
     * <p>{@code null} e a resposta certa para "o vanilla decide" — quem chama devolve o controle sem
     * inventar valor.
     */
    @Nullable
    public static Long seedFor(ResourceKey<Level> dimension) {
        return SEEDS.get().get(dimension);
    }

    /** Se esta dimensao e gerenciada por este mod (terreno, barreira e portais). */
    public static boolean isManaged(ResourceKey<Level> dimension) {
        return SEEDS.get().containsKey(dimension);
    }

    /** Todas as dimensoes declaradas. Usado no boot, para desamarrar e restaurar as barreiras. */
    public static Set<ResourceKey<Level>> managed() {
        return SEEDS.get().keySet();
    }

    /**
     * Se um portal pode <b>cavar</b> um portal novo nesta dimensao, gerando terreno na hora.
     *
     * <p>Negado por padrao. Ver o comentario de {@code allowRuntimeGeneration} na config: e a trava
     * que separa "o mundo foi pre-gerado e importado" de "o servidor gera no meio do horario de
     * pico".
     */
    public static boolean allowsRuntimeGeneration(ResourceKey<Level> dimension) {
        return RUNTIME_GENERATION.get().contains(dimension);
    }

    // --- Leitura da config -----------------------------------------------------------------------

    private static List<? extends String> rawSeeds() {
        return read(MundosConfig.SEEDS);
    }

    private static List<? extends String> rawRuntimeGeneration() {
        return read(MundosConfig.RUNTIME_GENERATION);
    }

    /**
     * Ler config antes de ela carregar lanca {@code IllegalStateException}. Aqui isso vira "nenhum
     * mundo declarado", que faz todo o mod se comportar como se nao estivesse instalado.
     *
     * <p>Degradar para o vanilla e deliberado: um mixin de seed que estoura durante a criacao das
     * dimensoes derruba o servidor no boot, e um servidor no ar com terreno vanilla e um problema
     * muito menor do que um servidor que nao sobe.
     */
    private static List<? extends String> read(net.neoforged.neoforge.common.ModConfigSpec.ConfigValue<List<? extends String>> value) {
        try {
            return value.get();
        } catch (IllegalStateException exception) {
            if (!warnedNotLoaded) {
                warnedNotLoaded = true;
                AurorionMundos.LOGGER.warn(
                        "Config lida antes de carregar; nenhum mundo sera gerenciado ate o servidor subir.");
            }
            return List.of();
        }
    }

    // --- Parsing ---------------------------------------------------------------------------------

    /**
     * Formato de cada entrada: {@code <dimensao>=<seed>}. Entrada invalida e ignorada, com log — um
     * erro de digitacao no seed de um mundo nao pode impedir os outros de subirem.
     *
     * <p>Visivel para teste de proposito: este formato e digitado a mao pelo admin, entao como ele
     * reage a erro de digitacao e comportamento observavel, nao detalhe interno.
     */
    static Map<ResourceKey<Level>, Long> parseSeeds(List<? extends String> raw) {
        Map<ResourceKey<Level>, Long> parsed = new HashMap<>(raw.size());

        for (String entry : raw) {
            int separator = entry.indexOf('=');
            if (separator <= 0) {
                AurorionMundos.LOGGER.error("Seed ignorado, falta o '=': {}", entry);
                continue;
            }

            ResourceKey<Level> dimension = parseDimension(entry.substring(0, separator).trim());
            if (dimension == null) continue;

            String number = entry.substring(separator + 1).trim();
            try {
                parsed.put(dimension, Long.parseLong(number));
            } catch (NumberFormatException exception) {
                AurorionMundos.LOGGER.error("Seed de '{}' ignorado, nao e um numero: {}", dimension.location(), number);
            }
        }

        return Map.copyOf(parsed);
    }

    private static Set<ResourceKey<Level>> parseDimensions(List<? extends String> raw) {
        Set<ResourceKey<Level>> parsed = new HashSet<>(raw.size());

        for (String entry : raw) {
            ResourceKey<Level> dimension = parseDimension(entry.trim());
            if (dimension != null) {
                parsed.add(dimension);
            }
        }

        return Set.copyOf(parsed);
    }

    @Nullable
    private static ResourceKey<Level> parseDimension(String raw) {
        ResourceLocation id = ResourceLocation.tryParse(raw);
        if (id == null) {
            AurorionMundos.LOGGER.error("Dimensao ignorada, id invalido: {}", raw);
            return null;
        }
        return ResourceKey.create(Registries.DIMENSION, id);
    }
}
