package com.aurorion.trama.skill;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Generated catalog travels in the jar beside Pufferfish data. No PDF needed at runtime. */
public final class SkillCatalog {
    /** Nome, tipo e texto de cada no, para a aba Habilidades da ficha. Preenchido no mesmo {@link #load}. */
    public record Node(String name, String kind, String description) {}
    private static final Map<String, Node> NODES = new HashMap<>();
    private static final Map<String, Map<Rating, Integer>> RATINGS = load();
    private static final List<String> STAFF_ONLY = RATINGS.keySet().stream().filter(SkillCatalog::staffOnly).sorted().toList();
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
                NODES.put(node.get("id").getAsString(), new Node(text(node, "name"), text(node, "kind"), text(node, "description")));
            }
            if (result.size() != 283) throw new IllegalStateException("Catalogo da Trama incompleto");
            return Map.copyOf(result);
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }
    private static String text(JsonObject node, String member) {
        return node.has(member) && node.get(member).isJsonPrimitive() ? node.get(member).getAsString() : "";
    }
    public static boolean contains(String id) { return RATINGS.containsKey(id); }
    /** Todos os ids, para sugerir no comando da staff. */
    public static Set<String> ids() { return RATINGS.keySet(); }
    /** Nos que so a staff concede (o Despertar Arcano): nenhum jogador compra, o sync forca o estado. */
    public static boolean staffOnly(String id) { Node node = NODES.get(id); return node != null && "Arcano".equals(node.kind()); }
    public static List<String> staffOnlyIds() { return STAFF_ONLY; }
    public static Node node(String id) { return NODES.get(id); }
    public static Map<Rating, Integer> ratings(String id) { return RATINGS.getOrDefault(id, Map.of()); }
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
