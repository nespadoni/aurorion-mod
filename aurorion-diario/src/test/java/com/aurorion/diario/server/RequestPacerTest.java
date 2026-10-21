package com.aurorion.diario.server;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RequestPacerTest {
    private final AtomicLong clock = new AtomicLong(10_000L);
    private final List<Runnable> agenda = new ArrayList<>();
    private final List<Long> delays = new ArrayList<>();

    private RequestPacer pacer(int slots) {
        return new RequestPacer(3_000L, slots, clock::get, (task, delay) -> {
            agenda.add(task);
            delays.add(delay);
            return true;
        });
    }

    private void runAgenda() {
        List<Runnable> now = new ArrayList<>(agenda);
        agenda.clear();
        now.forEach(Runnable::run);
    }

    /** O salvamento automatico que chega cedo espera a vez, e a versao mais nova toma o lugar da antiga. */
    @Test
    void earlyRequestWaitsAndNewerReplacesIt() {
        RequestPacer pacer = pacer(4);
        List<String> ran = new ArrayList<>();
        assertEquals(RequestPacer.Result.RAN, pacer.submit("p1", "sessao", () -> ran.add("v1")));
        clock.addAndGet(1_000L);
        assertEquals(RequestPacer.Result.DEFERRED, pacer.submit("p1", "sessao", () -> ran.add("v2")));
        assertEquals(RequestPacer.Result.COALESCED, pacer.submit("p1", "sessao", () -> ran.add("v3")));
        assertEquals(List.of(2_000L), delays);
        assertEquals(List.of("v1"), ran);

        clock.addAndGet(2_000L);
        runAgenda();
        assertEquals(List.of("v1", "v3"), ran);
    }

    @Test
    void differentSessionsNeverOverwriteEachOtherAndRespectTheInterval() {
        RequestPacer pacer = pacer(4);
        List<String> ran = new ArrayList<>();
        pacer.submit("p1", "a", () -> ran.add("a1"));
        pacer.submit("p1", "a", () -> ran.add("a2"));
        pacer.submit("p1", "b", () -> ran.add("b1"));
        clock.addAndGet(3_000L);
        runAgenda();
        assertEquals(List.of("a1", "a2"), ran);
        assertEquals(1, pacer.waitingCount());
        clock.addAndGet(3_000L);
        runAgenda();
        assertEquals(List.of("a1", "a2", "b1"), ran);
        assertEquals(0, pacer.waitingCount());
    }

    @Test
    void fullBucketRefusesExplicitly() {
        RequestPacer pacer = pacer(1);
        pacer.submit("p1", "a", () -> { });
        assertEquals(RequestPacer.Result.DEFERRED, pacer.submit("p1", "b", () -> { }));
        assertEquals(RequestPacer.Result.REFUSED, pacer.submit("p1", "c", () -> { }));
        assertEquals(RequestPacer.Result.RAN, pacer.submit("p2", "a", () -> { }), "outro perfil tem o proprio balde");
    }

    /** Sair roda o que esperava, e nao devolve o intervalo: reconectar nao compra chamada nova. */
    @Test
    void logoutFlushesWaitingWorkButKeepsTheInterval() {
        RequestPacer pacer = pacer(4);
        List<String> ran = new ArrayList<>();
        pacer.submit("p1", "a", () -> ran.add("a1"));
        pacer.submit("p1", "a", () -> ran.add("a2"));
        pacer.flush("p1");
        assertEquals(List.of("a1", "a2"), ran);
        runAgenda(); // o disparo agendado nao repete o que o flush ja rodou
        assertEquals(List.of("a1", "a2"), ran);
        assertEquals(RequestPacer.Result.DEFERRED, pacer.submit("p1", "a", () -> ran.add("a3")));
    }
}
