package com.aurorion.areas.data;

import com.aurorion.areas.geometry.*;
import com.aurorion.areas.region.AreaRegion;
import com.aurorion.areas.rules.*;
import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Formato unico para persistencia e perfis. Toda entrada passa pelas validacoes do dominio. */
public final class AreaJson {
    private AreaJson() {}
    public static JsonObject writeRules(AreaRules rules) {
        JsonObject json = new JsonObject(), flags = new JsonObject();
        rules.flags().forEach((key, value) -> flags.addProperty(key, value.name().toLowerCase(Locale.ROOT)));
        json.add("flags", flags);
        if (rules.ambience() != null) json.addProperty("ambience", rules.ambience().toString());
        if (rules.mobHealth() >= 0) json.addProperty("mob_health", rules.mobHealth());
        if (rules.mobDamage() >= 0) json.addProperty("mob_damage", rules.mobDamage());
        return json;
    }
    public static AreaRules readRules(JsonObject json) {
        Map<String, Decision> flags = new HashMap<>();
        if (json.has("flags")) for (var entry : json.getAsJsonObject("flags").entrySet()) {
            flags.put(entry.getKey(), Decision.valueOf(entry.getValue().getAsString().toUpperCase(Locale.ROOT)));
        }
        return new AreaRules(flags, json.has("ambience") ? ResourceLocation.parse(json.get("ambience").getAsString()) : null,
                json.has("mob_health") ? json.get("mob_health").getAsDouble() : -1,
                json.has("mob_damage") ? json.get("mob_damage").getAsDouble() : -1);
    }
    public static JsonObject writeRegion(AreaRegion region) {
        JsonObject json = new JsonObject();
        json.addProperty("id", region.id()); json.addProperty("name", region.name());
        json.addProperty("dimension", region.dimension().toString());
        json.addProperty("priority", region.priority()); json.addProperty("enabled", region.enabled());
        json.add("parts", writeShapes(region.volume().parts()));
        json.add("holes", writeShapes(region.volume().holes()));
        json.add("rules", writeRules(region.rules()));
        JsonObject exceptions = new JsonObject();
        region.exceptions().forEach((actor, keys) -> {
            JsonArray values = new JsonArray(); keys.stream().sorted().forEach(values::add);
            exceptions.add(actor.toString(), values);
        });
        json.add("exceptions", exceptions);
        return json;
    }
    public static AreaRegion readRegion(JsonObject json) {
        Map<UUID, Set<String>> exceptions = new HashMap<>();
        if (json.has("exceptions")) for (var entry : json.getAsJsonObject("exceptions").entrySet()) {
            Set<String> keys = new HashSet<>();
            for (JsonElement key : entry.getValue().getAsJsonArray()) keys.add(key.getAsString());
            exceptions.put(UUID.fromString(entry.getKey()), keys);
        }
        return new AreaRegion(json.get("id").getAsString(), json.get("name").getAsString(),
                ResourceLocation.parse(json.get("dimension").getAsString()), json.get("priority").getAsInt(),
                json.get("enabled").getAsBoolean(),
                new AreaVolume(readShapes(json.getAsJsonArray("parts")), readShapes(json.getAsJsonArray("holes"))),
                readRules(json.getAsJsonObject("rules")), exceptions);
    }
    private static JsonArray writeShapes(List<AreaShape> shapes) {
        JsonArray array = new JsonArray();
        for (AreaShape shape : shapes) {
            JsonObject json = new JsonObject();
            json.addProperty("min_y", shape.bounds().minY()); json.addProperty("max_y", shape.bounds().maxY());
            if (shape.isCircle()) {
                json.addProperty("radius", shape.radius());
                json.addProperty("x", shape.points().getFirst().x()); json.addProperty("z", shape.points().getFirst().z());
            } else {
                JsonArray points = new JsonArray();
                for (Point2 p : shape.points()) { JsonArray pair = new JsonArray(); pair.add(p.x()); pair.add(p.z()); points.add(pair); }
                json.add("points", points);
            }
            array.add(json);
        }
        return array;
    }
    private static List<AreaShape> readShapes(JsonArray json) {
        if (json.size() > AreaVolume.MAX_PARTS) throw new IllegalArgumentException("Formas demais na area.");
        List<AreaShape> shapes = new ArrayList<>();
        for (JsonElement element : json) {
            JsonObject shape = element.getAsJsonObject();
            double minY = shape.get("min_y").getAsDouble(), maxY = shape.get("max_y").getAsDouble();
            if (shape.has("radius")) {
                shapes.add(AreaShape.circle(shape.get("x").getAsDouble(), shape.get("z").getAsDouble(),
                        shape.get("radius").getAsDouble(), minY, maxY));
            } else {
                List<Point2> points = new ArrayList<>();
                JsonArray vertices = shape.getAsJsonArray("points");
                if (vertices.size() > AreaShape.MAX_VERTICES) throw new IllegalArgumentException("Vertices demais.");
                for (JsonElement vertex : vertices) {
                    JsonArray pair = vertex.getAsJsonArray();
                    if (pair.size() != 2) throw new IllegalArgumentException("Vertice precisa de [x, z].");
                    points.add(new Point2(pair.get(0).getAsDouble(), pair.get(1).getAsDouble()));
                }
                shapes.add(AreaShape.polygon(points, minY, maxY));
            }
        }
        return shapes;
    }
}
