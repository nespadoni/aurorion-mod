package com.aurorion.integracao.outbox;

import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * O envelope de um fato no contrato v1 do backend ({@code docs/planejamento/contrato-integracao-v1.md}).
 *
 * <p>Fica separado da ponte com o jogo para poder ser testado sem o Minecraft — inclusive contra o
 * backend real. O {@code server_id} nao vai no corpo: o backend o deduz da credencial.
 */
public final class FactJson {
    public static final int SCHEMA_VERSION = 1;

    private FactJson() {
    }

    public static String encode(String eventId, String type, Instant occurredAt,
                                @Nullable UUID profile, @Nullable UUID character,
                                @Nullable String sourceKind, @Nullable String sourceId,
                                JsonObject payload) {
        JsonObject json = new JsonObject();
        json.addProperty("schema_version", SCHEMA_VERSION);
        json.addProperty("event_id", eventId);
        json.addProperty("type", type);
        json.addProperty("occurred_at", occurredAt.toString());
        if (profile != null) {
            JsonObject actor = new JsonObject();
            actor.addProperty("profile_uuid", profile.toString());
            if (character != null) actor.addProperty("character_id", character.toString());
            json.add("actor", actor);
        }
        if (sourceKind != null && sourceId != null) {
            JsonObject source = new JsonObject();
            source.addProperty("kind", sourceKind);
            source.addProperty("id", sourceId);
            json.add("source_ref", source);
        }
        json.add("payload", payload.deepCopy());
        return json.toString();
    }
}
