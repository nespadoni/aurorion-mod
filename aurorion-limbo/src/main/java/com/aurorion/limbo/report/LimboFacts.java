package com.aurorion.limbo.report;

import com.aurorion.core.integration.GameFacts;
import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

/**
 * O que da auditoria do Limbo vira acontecimento publico na linha do tempo do site.
 *
 * <p>Pendura no {@link AuditLog#record}, que ja e o funil unico de tudo que acontece no Limbo: nenhum
 * ponto da mecanica precisa saber que o site existe. So os marcos da historia de alguem saem daqui —
 * cair, sair, chegar ao fim. Ajustes de prazo, tentativas e itens usados ficam so na auditoria da
 * staff, que e o lugar deles.
 */
final class LimboFacts {
    /** Prefixo do detalhe que {@code LimboManager.expire} grava quando a conta acabou de morrer. */
    private static final String FINAL_DEATH_DETAIL = "morte_definitiva";

    private LimboFacts() {
    }

    static void publish(MinecraftServer server, AuditEvent event) {
        if (!GameFacts.installed()) return;
        JsonObject payload = new JsonObject();
        String type = typeOf(event, payload);
        if (type == null) return;
        GameFacts.publish(type, GameFacts.subject(server, event.player()), payload);
    }

    /** Visivel para teste: escolhe o tipo e preenche o payload; {@code null} = nao e publico. */
    @Nullable
    static String typeOf(AuditEvent event, JsonObject payload) {
        return switch (event.type()) {
            case QUEDA -> {
                payload.addProperty("deadline_seconds", Math.max(0L, event.remainingMillis() / 1000L));
                yield GameFacts.LIMBO_ENTERED;
            }
            case RESGATE -> exit(payload, "resgate");
            case PORTA_ATRAVESSADA -> exit(payload, "porta");
            case RETORNO_ADMIN -> exit(payload, "staff");
            // PRAZO_VENCIDO so e gravado quando a conta acabou de morrer; o prefixo confirma.
            case PRAZO_VENCIDO -> event.detail().startsWith(FINAL_DEATH_DETAIL) ? GameFacts.FINAL_DEATH : null;
            default -> null;
        };
    }

    private static String exit(JsonObject payload, String reason) {
        payload.addProperty("exit_reason", reason);
        return GameFacts.LIMBO_EXITED;
    }
}
