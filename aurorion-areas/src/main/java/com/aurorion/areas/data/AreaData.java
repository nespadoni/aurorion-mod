package com.aurorion.areas.data;

import com.aurorion.areas.geometry.*;
import com.aurorion.areas.region.*;
import com.aurorion.areas.rules.*;
import com.aurorion.core.data.SavedDataAccess;
import com.google.gson.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;
import java.util.*;

public final class AreaData extends SavedData {
    public static final int MAX_AREAS = 512;
    private static final SavedDataAccess<AreaData> ACCESS =
            new SavedDataAccess<>("aurorion_areas_regions", AreaData::new, AreaData::load);
    private final Map<String, AreaRegion> regions = new HashMap<>();
    private final Map<ResourceLocation, AreaRules> defaults = new HashMap<>();
    private Map<ResourceLocation, AreaIndex> indexes = Map.of();
    private List<AreaRegion> ordered = List.of();
    private long revision;

    public static AreaData get(MinecraftServer server) { return ACCESS.get(server); }
    private static AreaData load(CompoundTag tag, HolderLookup.Provider registries) {
        AreaData data = new AreaData();
        if (!tag.contains("DefinitionsUtf8")) return data;
        JsonObject root = JsonParser.parseString(new String(tag.getByteArray("DefinitionsUtf8"),
                java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        if (root.get("version").getAsInt() != 1) throw new IllegalStateException("Versao desconhecida dos dados de areas.");
        JsonArray areas = root.getAsJsonArray("areas");
        if (areas.size() > MAX_AREAS) throw new IllegalStateException("Limite de areas excedido no save.");
        for (JsonElement value : areas) {
            AreaRegion region = AreaJson.readRegion(value.getAsJsonObject());
            if (data.regions.putIfAbsent(region.id(), region) != null) throw new IllegalStateException("Area duplicada no save.");
        }
        for (var entry : root.getAsJsonObject("dimensions").entrySet()) {
            data.defaults.put(ResourceLocation.parse(entry.getKey()), AreaJson.readRules(entry.getValue().getAsJsonObject()));
        }
        data.rebuild();
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
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
        if (!regions.containsKey(region.id()) && regions.size() >= MAX_AREAS) throw new IllegalArgumentException("Limite de areas atingido.");
        regions.put(region.id(), region); changed();
    }
    public void delete(String id) {
        if (regions.remove(id) == null) throw new IllegalArgumentException("Area inexistente: " + id);
        changed();
    }
    public void setDefaults(ResourceLocation dimension, AreaRules rules) {
        defaults.put(dimension, rules); changed();
    }
    public void clearCharacter(UUID actor) {
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
