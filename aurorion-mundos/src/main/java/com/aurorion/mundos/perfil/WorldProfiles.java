package com.aurorion.mundos.perfil;

import com.aurorion.core.datapack.DatapackRegistry;
import com.aurorion.mundos.AurorionMundos;
import com.aurorion.mundos.config.MundosConfig;
import net.minecraft.core.registries.Registries;
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

/**
 * Os perfis de mundo dos datapacks, e qual deles vale em cada dimensao.
 *
 * <h2>Quem decide o perfil de uma dimensao</h2>
 *
 * <ol>
 *   <li>a config {@code [dificuldade].porMundo} — o que a staff escolheu por comando;</li>
 *   <li>o campo {@code dimensoes} do proprio perfil — o que o datapack declarou;</li>
 *   <li>a config {@code [dificuldade].global} — o padrao do servidor inteiro.</li>
 * </ol>
 *
 * <h2>Custo</h2>
 *
 * <p>A resposta de {@link #forLevel} e uma consulta a um mapa imutavel montado de antemao. Ele e
 * remontado so quando algo muda (reload de datapack, config, comando) — e de forma preguicosa, na
 * primeira consulta depois da mudanca, porque a primeira carga de datapack acontece <b>antes</b> de a
 * config do servidor existir.
 */
public final class WorldProfiles {
    public static final String DIRECTORY = "aurorion/perfis_mundo";

    private static final Comparator<WorldProfile> ORDER = Comparator
            .comparingInt(WorldProfile::order)
            .thenComparing(profile -> profile.id().toString());

    private static final DatapackRegistry<WorldProfile> REGISTRY =
            new DatapackRegistry<>(DIRECTORY, AurorionMundos.LOGGER, WorldProfile::codec, ORDER)
                    .onReload(profiles -> invalidate());

    private static volatile boolean dirty = true;
    private static volatile Map<ResourceKey<Level>, WorldProfile> byDimension = Map.of();
    @Nullable
    private static volatile WorldProfile global;

    private WorldProfiles() {
    }

    public static PreparableReloadListener listener() {
        return REGISTRY.listener();
    }

    public static List<WorldProfile> all() {
        return REGISTRY.all();
    }

    public static List<ResourceLocation> ids() {
        return REGISTRY.ids();
    }

    @Nullable
    public static WorldProfile get(ResourceLocation id) {
        return WorldProfile.NONE_ID.equals(id) ? WorldProfile.NONE : REGISTRY.get(id);
    }

    /** Algo mudou; a proxima consulta remonta o indice. */
    public static void invalidate() {
        dirty = true;
    }

    /**
     * O perfil em vigor nesta dimensao, ou {@code null} para vanilla — o caso de quase toda chamada,
     * resolvido com uma consulta de mapa.
     */
    @Nullable
    public static WorldProfile forLevel(ResourceKey<Level> dimension) {
        if (dirty) rebuild();
        WorldProfile profile = byDimension.get(dimension);
        if (profile == null) profile = global;
        return profile == null || profile.isNone() ? null : profile;
    }

    /** De onde vem o perfil desta dimensao, para o {@code /mundos dificuldade ver}. */
    public static String origin(ResourceKey<Level> dimension) {
        if (dirty) rebuild();
        if (configured().containsKey(dimension)) return "config";
        for (WorldProfile profile : REGISTRY.all()) {
            if (profile.dimensions().contains(dimension)) return "datapack";
        }
        return global == null ? "vanilla" : "global";
    }

    private static synchronized void rebuild() {
        if (!dirty) return;
        // Limpa antes de montar: um invalidate() no meio da montagem deixa sujo de novo, e nao se perde.
        dirty = false;
        boolean configReady = MundosConfig.SPEC.isLoaded();

        Map<ResourceKey<Level>, WorldProfile> built = new HashMap<>();
        for (WorldProfile profile : REGISTRY.all()) {
            for (ResourceKey<Level> dimension : profile.dimensions()) built.putIfAbsent(dimension, profile);
        }
        WorldProfile fallback = null;
        if (configReady) {
            built.putAll(configured());
            fallback = parseProfile(MundosConfig.GLOBAL_PROFILE.get(), "global");
        }
        byDimension = Map.copyOf(built);
        global = fallback;
        // Sem config ainda (primeira carga de datapack), fica sujo: a consulta seguinte tenta de novo.
        if (!configReady) dirty = true;
    }

    private static Map<ResourceKey<Level>, WorldProfile> configured() {
        if (!MundosConfig.SPEC.isLoaded()) return Map.of();
        Map<ResourceKey<Level>, WorldProfile> out = new HashMap<>();
        for (String entry : MundosConfig.PROFILE_BY_WORLD.get()) {
            int split = entry.indexOf('=');
            if (split <= 0) continue;
            ResourceLocation dimension = ResourceLocation.tryParse(entry.substring(0, split).trim());
            WorldProfile profile = parseProfile(entry.substring(split + 1), entry);
            if (dimension == null || profile == null) continue;
            out.put(ResourceKey.create(Registries.DIMENSION, dimension), profile);
        }
        return out;
    }

    @Nullable
    private static WorldProfile parseProfile(String raw, String where) {
        String text = raw == null ? "" : raw.trim();
        if (text.isEmpty()) return null;
        ResourceLocation id = ResourceLocation.tryParse(text.indexOf(':') < 0 ? AurorionMundos.MOD_ID + ":" + text : text);
        WorldProfile profile = id == null ? null : get(id);
        if (profile == null) AurorionMundos.LOGGER.warn("Perfil de mundo '{}' nao existe ({}); ignorado.", text, where);
        return profile;
    }

    // --- Escrita pela staff ----------------------------------------------------------------------

    public static void setGlobal(@Nullable ResourceLocation profile) {
        MundosConfig.GLOBAL_PROFILE.set(profile == null ? "" : profile.toString());
        MundosConfig.SPEC.save();
        invalidate();
    }

    /** @param profile {@code null} tira a dimensao da config, devolvendo-a ao datapack/global */
    public static void setFor(ResourceKey<Level> dimension, @Nullable ResourceLocation profile) {
        List<String> entries = new ArrayList<>();
        String prefix = dimension.location() + "=";
        for (String entry : MundosConfig.PROFILE_BY_WORLD.get()) {
            if (!entry.startsWith(prefix)) entries.add(entry);
        }
        if (profile != null) entries.add(prefix + profile);
        MundosConfig.PROFILE_BY_WORLD.set(entries);
        MundosConfig.SPEC.save();
        invalidate();
    }
}
