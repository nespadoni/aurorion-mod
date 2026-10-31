package com.aurorion.ficha.sheet;

import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

/**
 * Qual ficha este perfil usa: {@code source} {@code explicit} (escolhida em /ficha vincular ou no
 * site) ou {@code default} (conta principal → ficha ativa). {@code stale}: o vínculo explícito deixou
 * de valer (a pessoa não controla mais aquela ficha/NPC); o site o ignora e avisa.
 */
public record Binding(String source, String subjectType, long subjectId, long rpgCharacterId, boolean stale) {
    public Binding {
        source = FichaSheet.text(source);
        subjectType = FichaSheet.text(subjectType);
    }

    public boolean explicit() {
        return "explicit".equals(source);
    }

    @Nullable
    public static Binding parse(@Nullable JsonObject o) {
        if (o == null) return null;
        return new Binding(Json.str(o, "source", 16), Json.str(o, "subject_type", 8), Json.number(o, "subject_id"),
                Json.number(o, "rpg_character_id"), Json.bool(o, "stale"));
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("source", source);
        o.addProperty("subject_type", subjectType);
        o.addProperty("subject_id", subjectId);
        o.addProperty("rpg_character_id", rpgCharacterId);
        if (stale) o.addProperty("stale", true);
        return o;
    }
}
