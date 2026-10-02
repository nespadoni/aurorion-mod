package com.aurorion.trama.skill;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Generated catalog travels in the jar beside Pufferfish data. No PDF needed at runtime. */
public final class SkillCatalog {
    private static final Map<String, Map<Rating, Integer>> RATINGS = load();
    private SkillCatalog() {}
    private static Map<String, Map<Rating, Integer>> load() {
        var result = new HashMap<String, Map<Rating, Integer>>();
        String path = "/data/aurorion_trama/aurorion/trama/catalog.json";
        try (var stream = Objects.requireNonNull(SkillCatalog.class.getResourceAsStream(path), path);
             var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            for (var element : JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("nodes")) {
                var node = element.getAsJsonObject();
                var ratings = new EnumMap<Rating, Integer>(Rating.class);
                if (node.has("ratings")) node.getAsJsonObject("ratings").entrySet().forEach(e ->
                        ratings.put(Rating.valueOf(e.getKey()), e.getValue().getAsInt()));
                result.put(node.get("id").getAsString(), Map.copyOf(ratings));
            }
            if (result.size() != 271) throw new IllegalStateException("Catalogo da Trama incompleto");
            return Map.copyOf(result);
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }
    public static boolean contains(String id) { return RATINGS.containsKey(id); }
    public static EnumMap<Rating, Double> sum(Set<String> unlocked) {
        var sum = new EnumMap<Rating, Double>(Rating.class);
        for (Rating r : Rating.values()) sum.put(r, 0.0);
        for (String id : unlocked) RATINGS.getOrDefault(id, Map.of())
                .forEach((r, value) -> sum.merge(r, value.doubleValue(), Double::sum));
        sum.replaceAll((r, value) -> r == Rating.MOV && unlocked.contains("C19")
                ? Math.max(0, Math.min(.06/r.step, value))
                : r == Rating.MAG && unlocked.contains("AV9")
                ? Math.max(0, Math.min(.12/r.step, value)) : r.clamp(value));
        return sum;
    }
}
