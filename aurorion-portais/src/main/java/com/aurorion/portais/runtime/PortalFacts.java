package com.aurorion.portais.runtime;

import com.aurorion.core.integration.GameFacts;
import com.aurorion.portais.AurorionPortais;
import com.aurorion.portais.config.TransitConfig;
import com.aurorion.portais.line.TransitLine;
import com.google.gson.JsonObject;

import java.time.Instant;

/**
 * Abertura e fechamento de uma janela de portal, para a linha do tempo do site.
 *
 * <p>Sai do mesmo lugar que o aviso no chat ({@link TransitAnnouncer}), mas nao depende dele: o
 * anuncio pode estar desligado e o fato continua valendo. A unica condicao e a trava estar ligada —
 * com {@code enforce} desligado o relogio corre, mas qualquer um atravessa, e "o portal abriu" nao
 * seria verdade no jogo.
 */
final class PortalFacts {
    private PortalFacts() {
    }

    static void opened(TransitLine line, long closesAtMillis) {
        if (!publishes()) return;
        try {
            JsonObject payload = base(line, "open");
            payload.addProperty("closes_at", Instant.ofEpochMilli(closesAtMillis).toString());
            GameFacts.publish(GameFacts.PORTAL_STATE, null, payload);
        } catch (RuntimeException e) {
            // O relogio da linha nao pode parar porque o aviso ao site falhou.
            AurorionPortais.LOGGER.warn("Nao consegui publicar a abertura da linha {}", line.id(), e);
        }
    }

    static void closed(TransitLine line) {
        if (!publishes()) return;
        try {
            GameFacts.publish(GameFacts.PORTAL_STATE, null, base(line, "closed"));
        } catch (RuntimeException e) {
            AurorionPortais.LOGGER.warn("Nao consegui publicar o fechamento da linha {}", line.id(), e);
        }
    }

    private static boolean publishes() {
        return GameFacts.installed() && TransitConfig.ENFORCE.get();
    }

    private static JsonObject base(TransitLine line, String state) {
        JsonObject payload = new JsonObject();
        payload.addProperty("line_id", line.id().toString());
        payload.addProperty("line_name", line.name().getString());
        payload.addProperty("state", state);
        if (!line.dimensions().isEmpty()) {
            payload.addProperty("destination", line.dimensions().get(0).location().toString());
        }
        return payload;
    }
}
