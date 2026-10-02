package com.aurorion.integracao.outbox;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutboxTest {
    private final AtomicLong clock = new AtomicLong(1_000_000L);
    private final List<String> warnings = new ArrayList<>();
    private final Outbox.Log log = new Outbox.Log() {
        @Override public void info(String message) { }
        @Override public void warn(String message) { warnings.add(message); }
    };

    private static Path spoolIn(Path dir) {
        return dir.resolve("aurorion_integracao").resolve("pendentes.jsonl");
    }

    private Outbox outbox(Path spool, long max, Function<List<String>, Attempt> transport) {
        return new Outbox(spool, max, transport, log, clock::get, false);
    }

    private static Attempt delivered(boolean... resolved) {
        return new Attempt(Attempt.Kind.DELIVERED, resolved, 0, List.of(), "HTTP 200");
    }

    private static Attempt allDelivered(List<String> batch) {
        boolean[] resolved = new boolean[batch.size()];
        java.util.Arrays.fill(resolved, true);
        return delivered(resolved);
    }

    @Test
    void diagnosticsAreReadOnlyAndDoNotExposeFactsOrTransportDetails() throws Exception {
        int[] calls = {0};
        Outbox box = outbox(spoolIn(Files.createTempDirectory("outbox")), 1 << 20,
                batch -> { calls[0]++; return Attempt.retry(0, "private-transport-detail"); });
        box.offer("private-fact-payload");
        var queued = box.diagnostics();
        assertEquals(1, queued.queued());
        assertEquals(0, queued.pending());
        assertEquals(0, calls[0]);
        assertFalse(queued.toString().contains("private-fact"));
        box.runOnce(0);
        var waiting = box.diagnostics();
        assertEquals(0, waiting.queued());
        assertEquals(1, waiting.pending());
        assertTrue(waiting.retryInMillis() > 0);
        assertFalse(waiting.toString().contains("private-transport"));
        assertEquals(1, calls[0]);
    }

    @Test
    void factSurvivesOutageAndRestart() throws Exception {
        Path spool = spoolIn(Files.createTempDirectory("outbox"));
        Outbox down = outbox(spool, 1 << 20, batch -> Attempt.retry(0, "rede"));
        assertTrue(down.offer("{\"event_id\":\"a\"}"));
        down.runOnce(0);
        assertEquals(List.of("{\"event_id\":\"a\"}"), Files.readAllLines(spool, StandardCharsets.UTF_8),
                "o fato precisa estar em disco antes de qualquer entrega");

        List<List<String>> sent = new ArrayList<>();
        Outbox up = outbox(spool, 1 << 20, batch -> { sent.add(List.copyOf(batch)); return allDelivered(batch); });
        assertEquals(1, up.backlog(), "o reinicio recupera o pendente do disco");
        up.runOnce(0);
        assertEquals(List.of(List.of("{\"event_id\":\"a\"}")), sent);
        assertFalse(Files.exists(spool), "entregue tudo, o spool some");
    }

    @Test
    void onlyAnsweredFactsLeaveTheSpool() throws Exception {
        Path spool = spoolIn(Files.createTempDirectory("outbox"));
        Outbox box = outbox(spool, 1 << 20, batch -> delivered(true, false, true));
        box.offer("1");
        box.offer("2");
        box.offer("3");
        box.runOnce(0);
        assertEquals(List.of("2"), Files.readAllLines(spool, StandardCharsets.UTF_8));
        assertEquals(1, box.backlog());
    }

    @Test
    void retryWaitsBeforeTryingAgain() throws Exception {
        Path spool = spoolIn(Files.createTempDirectory("outbox"));
        int[] calls = {0};
        Outbox box = outbox(spool, 1 << 20, batch -> { calls[0]++; return Attempt.retry(0, "HTTP 503"); });
        box.offer("x");
        box.runOnce(0);
        box.runOnce(0);
        assertEquals(1, calls[0], "sem esperar o backoff, nao tenta de novo");
        clock.addAndGet(10_000L);
        box.runOnce(0);
        assertEquals(2, calls[0], "passado o backoff, tenta de novo");
    }

    @Test
    void forbiddenWarnsOnceAndWaitsLong() throws Exception {
        Path spool = spoolIn(Files.createTempDirectory("outbox"));
        int[] calls = {0};
        Outbox box = outbox(spool, 1 << 20, batch -> { calls[0]++; return Attempt.forbidden("HTTP 403"); });
        box.offer("x");
        box.runOnce(0);
        clock.addAndGet(5 * 60_000L);
        box.runOnce(0);
        assertEquals(1, calls[0], "credencial recusada nao vira martelada no site");
        assertEquals(1, warnings.stream().filter(w -> w.contains("credencial")).count());
        assertTrue(Files.exists(spool), "os fatos ficam guardados ate a credencial ser corrigida");
    }

    @Test
    void largeBacklogGoesInBatchesOfFifty() throws Exception {
        Path spool = spoolIn(Files.createTempDirectory("outbox"));
        List<Integer> sizes = new ArrayList<>();
        Outbox box = outbox(spool, 1 << 20, batch -> { sizes.add(batch.size()); return allDelivered(batch); });
        for (int i = 0; i < 120; i++) box.offer("{\"n\":" + i + "}");
        box.runOnce(0);
        assertEquals(List.of(50, 50, 20), sizes);
        assertEquals(0, box.backlog());
    }

    @Test
    void fullSpoolDropsWithWarningInsteadOfGrowing() throws Exception {
        Path spool = spoolIn(Files.createTempDirectory("outbox"));
        Outbox box = outbox(spool, 10, batch -> Attempt.retry(0, "rede"));
        box.offer("12345");
        box.offer("67890");
        box.runOnce(0);
        assertEquals(1, box.backlog(), "so cabe uma linha de 6 bytes num spool de 10");
        assertTrue(warnings.stream().anyMatch(w -> w.contains("descartado")));
    }

    @Test
    void spoolWithBlankLinesIsTolerated() throws IOException, InterruptedException {
        Path spool = spoolIn(Files.createTempDirectory("outbox"));
        Files.createDirectories(spool.getParent());
        Files.writeString(spool, "a\n\n b\n", StandardCharsets.UTF_8);
        List<String> sent = new ArrayList<>();
        Outbox box = outbox(spool, 1 << 20, batch -> { sent.addAll(batch); return allDelivered(batch); });
        box.runOnce(0);
        assertEquals(List.of("a", " b"), sent);
    }
}
