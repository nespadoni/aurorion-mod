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

    /** 400/413/422 recusam o lote pelo conteudo: a caixa divide o lote em vez de reenviar igual para sempre. */
    @Test
    void contentErrorsAreNotRetriedForever() {
        assertEquals(Attempt.Kind.MALFORMED, IngestClient.interpret(400, "", null, 2).kind());
        assertEquals(Attempt.Kind.MALFORMED, IngestClient.interpret(413, "", null, 2).kind());
        assertEquals(Attempt.Kind.MALFORMED, IngestClient.interpret(422, "", null, 2).kind());
        assertEquals(Attempt.Kind.RETRY, IngestClient.interpret(404, "", null, 2).kind());
    }

    /** Resposta com tipos errados nao derruba a thread da caixa. */
    @Test
    void oddItemTypesDoNotThrow() {
        String body = "{\"results\":[{\"result\":{\"x\":1}},{\"result\":null,\"code\":[1]},"
                + "{\"result\":\"rejected\",\"code\":{\"a\":1}},\"texto\",{\"result\":\"accepted\"}]}";
        Attempt attempt = IngestClient.interpret(200, body, null, 5);
        assertArrayEquals(new boolean[]{false, false, true, false, true}, attempt.resolved());
        assertEquals(List.of("sem_codigo"), attempt.rejections());
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
