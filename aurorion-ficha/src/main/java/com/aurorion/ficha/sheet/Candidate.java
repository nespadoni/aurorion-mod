package com.aurorion.ficha.sheet;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Uma ficha que o perfil pode usar: a ficha ativa da pessoa ({@code kind=sheet}), um NPC ligado a
 * ela ({@code kind=npc}) ou, para staff, um NPC livre ({@code staff_only=true}; vincular liga o NPC
 * à pessoa no site). {@code bound_profiles}: outros perfis que já usam esta ficha.
 */
public record Candidate(String subjectType, long subjectId, String kind, String name, String title, String portraitUrl,
                        boolean staffOnly, List<String> boundProfiles) {
    /** 1 ficha + até 200 NPCs livres do contrato + folga para os NPCs ligados. */
    public static final int MAX = 240;
    /** Perfis que já usam a ficha: o site manda o nome do perfil (até 32) ou, sem nome, o UUID (36). */
    public static final int MAX_BOUND = 8;
    public static final int MAX_BOUND_NAME = 36;

    public Candidate {
        subjectType = FichaSheet.text(subjectType);
        kind = kind == null || kind.isEmpty() ? subjectType : kind;
        name = FichaSheet.text(name);
        title = FichaSheet.text(title);
        portraitUrl = FichaSheet.text(portraitUrl);
        boundProfiles = FichaSheet.list(boundProfiles);
    }

    public boolean npc() {
        return "npc".equals(kind) || "npc".equals(subjectType);
    }

    public boolean sameAs(String type, long id) {
        return subjectType.equals(type) && subjectId == id;
    }

    public static Candidate parse(JsonObject o) {
        return new Candidate(Json.str(o, "subject_type", 8), Json.number(o, "subject_id"), Json.str(o, "kind", 8),
                Json.str(o, "name", FichaSheet.MAX_NAME), Json.str(o, "title", FichaSheet.MAX_TITLE),
                Json.link(o, "portrait_url", FichaSheet.MAX_URL), Json.bool(o, "staff_only"),
                Json.strings(Json.array(o, "bound_profiles"), MAX_BOUND, MAX_BOUND_NAME));
    }

    /**
     * Só {@code sheet}/{@code npc} com id: o resto o {@code /bind} recusaria. A mesma ficha repetida
     * (ligada e também livre, numa corrida no site) entra uma vez.
     */
    public static List<Candidate> parseList(@Nullable JsonArray array) {
        List<Candidate> out = Json.objects(array, MAX, Candidate::parse);
        java.util.Set<String> seen = new java.util.HashSet<>();
        out.removeIf(c -> !("sheet".equals(c.subjectType()) || "npc".equals(c.subjectType())) || c.subjectId() <= 0
                || !seen.add(c.subjectType() + ":" + c.subjectId()));
        return List.copyOf(out);
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("subject_type", subjectType);
        o.addProperty("subject_id", subjectId);
        o.addProperty("kind", kind);
        o.addProperty("name", name);
        o.addProperty("title", title);
        o.addProperty("portrait_url", portraitUrl);
        o.addProperty("staff_only", staffOnly);
        o.add("bound_profiles", Json.toArray(boundProfiles));
        return o;
    }

    public static JsonArray toJson(List<Candidate> list) {
        JsonArray out = new JsonArray();
        for (Candidate c : list) out.add(c.toJson());
        return out;
    }
}
