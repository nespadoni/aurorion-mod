package com.aurorion.limbo.report;

import com.aurorion.core.integration.GameFacts;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LimboFactsTest {
    private static AuditEvent event(AuditEvent.Type type, long remainingMillis, String detail) {
        return new AuditEvent(type, UUID.randomUUID(), "Sera", 0, remainingMillis, 0, 0, detail);
    }

    @Test
    void fallCarriesTheDeadline() {
        JsonObject payload = new JsonObject();
        assertEquals(GameFacts.LIMBO_ENTERED, LimboFacts.typeOf(event(AuditEvent.Type.QUEDA, 172_800_000L, ""), payload));
        assertEquals(172_800L, payload.get("deadline_seconds").getAsLong());
    }

    @Test
    void everyWayOutIsAnExitWithItsReason() {
        JsonObject rescued = new JsonObject();
        assertEquals(GameFacts.LIMBO_EXITED, LimboFacts.typeOf(event(AuditEvent.Type.RESGATE, 0, ""), rescued));
        assertEquals("resgate", rescued.get("exit_reason").getAsString());

        JsonObject door = new JsonObject();
        LimboFacts.typeOf(event(AuditEvent.Type.PORTA_ATRAVESSADA, 0, ""), door);
        assertEquals("porta", door.get("exit_reason").getAsString());

        JsonObject staff = new JsonObject();
        LimboFacts.typeOf(event(AuditEvent.Type.RETORNO_ADMIN, 0, ""), staff);
        assertEquals("staff", staff.get("exit_reason").getAsString());
    }

    @Test
    void onlyARealFinalDeathIsPublished() {
        assertEquals(GameFacts.FINAL_DEATH, LimboFacts.typeOf(
                event(AuditEvent.Type.PRAZO_VENCIDO, 0, "morte_definitiva personagem=abc"), new JsonObject()));
        assertNull(LimboFacts.typeOf(event(AuditEvent.Type.PRAZO_VENCIDO, 0, ""), new JsonObject()));
    }

    @Test
    void staffBookkeepingStaysInTheAudit() {
        for (AuditEvent.Type type : new AuditEvent.Type[]{AuditEvent.Type.PRAZO_AJUSTADO, AuditEvent.Type.TENTATIVA_RESGATE,
                AuditEvent.Type.FIO_USADO, AuditEvent.Type.RELICARIO_USADO, AuditEvent.Type.PORTA_ARMADA}) {
            assertNull(LimboFacts.typeOf(event(type, 0, ""), new JsonObject()), type.name());
        }
    }
}
