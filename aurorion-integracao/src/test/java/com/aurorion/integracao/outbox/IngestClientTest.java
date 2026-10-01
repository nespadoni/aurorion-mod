package com.aurorion.integracao.outbox;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class IngestClientTest {

    @Test
    void bodyWrapsLinesWithoutReencoding() {
        assertEquals("{\"events\":[{\"a\":1},{\"b\":2}]}", IngestClient.body(List.of("{\"a\":1}", "{\"b\":2}")));
    }

    @Test
    void itemResultsResolveByPosition() {
        String body = "{\"results\":[{\"event_id\":\"a\",\"result\":\"accepted\"},"
                + "{\"event_id\":\"b\",\"result\":\"duplicate\"},"
                + "{\"event_id\":\"c\",\"result\":\"rejected\",\"code\":\"unknown_type\"}]}";
        Attempt attempt = IngestClient.interpret(200, body, null, 4);
        assertEquals(Attempt.Kind.DELIVERED, attempt.kind());
        assertArrayEquals(new boolean[]{true, true, true, false}, attempt.resolved(),
                "o quarto fato ficou sem resposta e precisa voltar depois");
        assertEquals(List.of("unknown_type"), attempt.rejections());
    }

    @Test
    void credentialErrorsAreNotRetriedLikeOutages() {
        assertEquals(Attempt.Kind.FORBIDDEN, IngestClient.interpret(403, "", null, 1).kind());
        assertEquals(Attempt.Kind.FORBIDDEN, IngestClient.interpret(401, "", null, 1).kind());
        assertEquals(Attempt.Kind.RETRY, IngestClient.interpret(503, "", null, 1).kind());
        assertEquals(Attempt.Kind.RETRY, IngestClient.interpret(200, "<html>", null, 1).kind());
    }

    @Test
    void tooManyRequestsHonorsRetryAfter() {
        Attempt attempt = IngestClient.interpret(429, "", "7", 1);
        assertEquals(Attempt.Kind.RETRY, attempt.kind());
        assertEquals(7_000L, attempt.waitMillis());
        assertEquals(30_000L, IngestClient.retryAfterMillis("amanha"));
        assertEquals(3_600_000L, IngestClient.retryAfterMillis("999999"));
    }
}
