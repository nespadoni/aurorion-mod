package com.aurorion.ficha.sheet;

import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

/**
 * Um cartão de rolagem como chega a um cliente: quem rolou, como esse cliente pode ver
 * ({@code view}) e a rolagem já redigida para ele.
 *
 * <p>{@code view}: {@code own} (quem rolou), {@code gm} (mestre: tudo, inclusive CD escondida e
 * bônus de NPC) ou {@code public} (vizinho). {@code characterName} é o nome da ficha que rolou (o
 * NPC, num alt); {@code rollerName}, o nome exibido do jogador.
 */
public record RollCard(String id, String rollerId, String rollerName, String characterName, String view, boolean secret,
                       String requestId, boolean dcHidden, boolean bonusHidden, RollResult roll) {
    public static final String OWN = "own";
    public static final String GM = "gm";
    public static final String PUBLIC = "public";

    /** Sem nulo; {@code view} desconhecido vira {@code public} (o menor privilégio de exibição). */
    public RollCard {
        id = FichaSheet.text(id);
        rollerId = FichaSheet.text(rollerId);
        rollerName = FichaSheet.text(rollerName);
        characterName = FichaSheet.text(characterName);
        view = OWN.equals(view) || GM.equals(view) ? view : PUBLIC;
        requestId = FichaSheet.text(requestId);
        java.util.Objects.requireNonNull(roll, "roll");
    }

    public boolean own() {
        return OWN.equals(view);
    }

    public boolean gm() {
        return GM.equals(view);
    }

    /** O nome que o cartão mostra: o da ficha quando houver, senão o do jogador. */
    public String displayName() {
        return characterName.isEmpty() ? rollerName : characterName;
    }

    @Nullable
    public static RollCard parse(@Nullable JsonObject o) {
        if (o == null) return null;
        RollResult roll = RollResult.parse(Json.object(o, "roll"));
        if (roll == null) return null;
        return new RollCard(Json.str(o, "id", 100), Json.str(o, "roller_id", 36), Json.str(o, "roller_name", 64),
                Json.str(o, "character_name", FichaSheet.MAX_NAME), Json.str(o, "view", 8), Json.bool(o, "secret"),
                Json.str(o, "request_id", 12), Json.bool(o, "dc_hidden"), Json.bool(o, "bonus_hidden"), roll);
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("id", id);
        o.addProperty("roller_id", rollerId);
        o.addProperty("roller_name", rollerName);
        o.addProperty("character_name", characterName);
        o.addProperty("view", view);
        o.addProperty("secret", secret);
        if (!requestId.isEmpty()) o.addProperty("request_id", requestId);
        if (dcHidden) o.addProperty("dc_hidden", true);
        if (bonusHidden) o.addProperty("bonus_hidden", true);
        o.add("roll", roll.toJson());
        return o;
    }
}
