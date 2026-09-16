package com.aurorion.areas.data;

import com.aurorion.areas.geometry.*;
import com.aurorion.areas.region.*;
import com.aurorion.areas.rules.*;
import com.aurorion.areas.AurorionAreas;
import com.aurorion.core.data.SavedDataAccess;
import com.google.gson.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;
import java.util.*;

public final class AreaData extends SavedData {
    public static final int MAX_AREAS = 512;
    private static final String ACCESS_NAME = "aurorion_areas_regions";
    private static final SavedDataAccess<AreaData> ACCESS =
            new SavedDataAccess<>(ACCESS_NAME, AreaData::new, AreaData::load);
    private final Map<String, AreaRegion> regions = new HashMap<>();
    private final Map<ResourceLocation, AreaRules> defaults = new HashMap<>();
    private Map<ResourceLocation, AreaIndex> indexes = Map.of();
    private List<AreaRegion> ordered = List.of();
    private long revision;
    /**
     * NBT original quando a leitura falhou. O {@code DimensionDataStorage} do vanilla engole a
     * excecao e pode criar um {@code SavedData} vazio; uma edicao seguida de autosave substituiria
     * as areas da staff. Preservar o composto inteiro mantem inclusive campos de versoes futuras
     * e valores com tipo inesperado. A copia so e criada neste caminho de erro.
     */
    @Nullable private CompoundTag unreadable;

    public static AreaData get(MinecraftServer server) { return ACCESS.get(server); }
    static AreaData load(CompoundTag tag, HolderLookup.Provider registries) { // visivel para teste
        AreaData data = new AreaData();
        try {
            if (!tag.contains("DefinitionsUtf8", Tag.TAG_BYTE_ARRAY)) {
                throw new IllegalStateException("DefinitionsUtf8 ausente ou com tipo NBT invalido.");
            }
            data.read(tag.getByteArray("DefinitionsUtf8"));
        } catch (RuntimeException error) {
            data.unreadable = tag.copy();
            data.regions.clear();
            data.defaults.clear();
            data.indexes = Map.of();
            data.ordered = List.of();
            AurorionAreas.LOGGER.error("Areas: nao foi possivel ler {}. O arquivo sera preservado e a edicao"
                    + " fica bloqueada ate que ele seja corrigido; nenhuma area esta em vigor.", ACCESS_NAME, error);
        }
        return data;
    }
    /** Le para variaveis locais: um erro no meio nunca deixa metade das areas em vigor. */
    private void read(byte[] raw) {
        JsonObject root = JsonParser.parseString(new String(raw, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        if (root.get("version").getAsInt() != 1) throw new IllegalStateException("Versao desconhecida dos dados de areas.");
        JsonArray areas = root.getAsJsonArray("areas");
        if (areas.size() > MAX_AREAS) throw new IllegalStateException("Limite de areas excedido no save.");
        Map<String, AreaRegion> loaded = new HashMap<>();
        for (JsonElement value : areas) {
            AreaRegion region = AreaJson.readRegion(value.getAsJsonObject());
            if (loaded.putIfAbsent(region.id(), region) != null) throw new IllegalStateException("Area duplicada no save.");
        }
        Map<ResourceLocation, AreaRules> loadedDefaults = new HashMap<>();
        for (var entry : root.getAsJsonObject("dimensions").entrySet()) {
            loadedDefaults.put(ResourceLocation.parse(entry.getKey()), AreaJson.readRules(entry.getValue().getAsJsonObject()));
        }
        regions.putAll(loaded);
        defaults.putAll(loadedDefaults);
        rebuild();
    }
    /** Enquanto os dados nao forem legiveis, nada pode ser gravado por cima deles. */
    public boolean readOnly() { return unreadable != null; }
    private void requireWritable() {
        if (unreadable != null) throw new IllegalArgumentException(
                "Os dados de areas nao puderam ser lidos (veja o log do servidor). A edicao esta bloqueada"
                + " para nao apagar o arquivo; corrija-o e reinicie o servidor.");
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        if (unreadable != null) return unreadable.copy();
        JsonObject root = new JsonObject(), dimensions = new JsonObject();
        root.addProperty("version", 1);
        JsonArray array = new JsonArray();
        for (AreaRegion region : ordered) array.add(AreaJson.writeRegion(region));
        root.add("areas", array);
        defaults.forEach((id, rules) -> dimensions.add(id.toString(), AreaJson.writeRules(rules)));
        root.add("dimensions", dimensions);
        tag.putByteArray("DefinitionsUtf8", root.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return tag;
    }
    public void put(AreaRegion region) {
        requireWritable();
        if (!regions.containsKey(region.id()) && regions.size() >= MAX_AREAS) throw new IllegalArgumentException("Limite de areas atingido.");
        regions.put(region.id(), region); changed();
    }
    public void delete(String id) {
        requireWritable();
        if (regions.remove(id) == null) throw new IllegalArgumentException("Area inexistente: " + id);
        changed();
    }
    public void setDefaults(ResourceLocation dimension, AreaRules rules) {
        requireWritable();
        defaults.put(dimension, rules); changed();
    }
    public void clearCharacter(UUID actor) {
        // O reset transacional precisa falhar e ser repetido apos o reparo, sem manter excecoes antigas.
        requireWritable();
        boolean changed = false;
        for (AreaRegion region : List.copyOf(regions.values())) {
            if (!region.exceptions().containsKey(actor)) continue;
            Map<UUID, Set<String>> exceptions = new HashMap<>(region.exceptions());
            exceptions.remove(actor);
            regions.put(region.id(), new AreaRegion(region.id(), region.name(), region.dimension(), region.priority(),
                    region.enabled(), region.volume(), region.rules(), exceptions));
            changed = true;
        }
        if (changed) changed();
    }
    private void changed() { rebuild(); setDirty(); }
    private void rebuild() {
        ordered = regions.values().stream().sorted(Comparator.comparing(AreaRegion::id)).toList();
        Map<ResourceLocation, List<AreaRegion>> grouped = new HashMap<>();
        for (AreaRegion region : ordered) grouped.computeIfAbsent(region.dimension(), ignored -> new ArrayList<>()).add(region);
        Map<ResourceLocation, AreaIndex> built = new HashMap<>();
        grouped.forEach((id, values) -> built.put(id, new AreaIndex(values)));
        indexes = Map.copyOf(built);
        revision++;
    }
    public void resolve(ResourceLocation dimension, double x, double y, double z, @Nullable UUID actor, ResolvedRules out) {
        out.reset(defaults(dimension), actor);
        AreaIndex index = indexes.get(dimension);
        if (index != null) index.resolve(x, y, z, out);
    }
    public boolean allows(ResourceLocation dimension, double x, double y, double z, @Nullable UUID actor, String key) {
        AreaIndex index = indexes.get(dimension);
        AreaRegion owner = index == null ? null : index.owner(x, y, z, key, actor);
        return (owner == null ? defaults(dimension).flag(key) : owner.decision(key, actor)) != Decision.DENY;
    }
    public AreaRules defaults(ResourceLocation dimension) { return defaults.getOrDefault(dimension, AreaRules.INHERIT); }
    public AreaRegion require(String id) {
        AreaRegion region = regions.get(id);
        if (region == null) throw new IllegalArgumentException("Area inexistente: " + id);
        return region;
    }
    public List<AreaRegion> all() { return ordered; }
    public long revision() { return revision; }
}
