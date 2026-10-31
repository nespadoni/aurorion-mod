package com.aurorion.ficha.network;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FloodGuardTest {
    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();

    @Test
    void burstThenSteadyRate() {
        AtomicLong now = new AtomicLong(1_000);
        FloodGuard guard = new FloodGuard(4, 2, now::get);
        for (int i = 0; i < 4; i++) assertEquals(FloodGuard.Verdict.PASS, guard.acquire(A), "rajada " + i);
        assertEquals(FloodGuard.Verdict.DROP_NOTIFY, guard.acquire(A), "o primeiro recusado avisa");
        assertEquals(FloodGuard.Verdict.DROP, guard.acquire(A), "os seguintes, não");
        assertEquals(FloodGuard.Verdict.PASS, guard.acquire(B), "cada jogador tem o seu balde");

        now.addAndGet(500); // 2 por segundo: meio segundo devolve uma ficha
        assertEquals(FloodGuard.Verdict.PASS, guard.acquire(A));
        assertEquals(FloodGuard.Verdict.DROP, guard.acquire(A), "aviso no máximo a cada 2 s");
        now.addAndGet(2_000);
        for (int i = 0; i < 4; i++) assertEquals(FloodGuard.Verdict.PASS, guard.acquire(A), "encheu de novo até o teto");
        assertEquals(FloodGuard.Verdict.DROP_NOTIFY, guard.acquire(A), "passaram 2 s: avisa outra vez");
    }

    @Test
    void refillNeverPassesCapacity() {
        AtomicLong now = new AtomicLong(0);
        FloodGuard guard = new FloodGuard(3, 10, now::get);
        guard.acquire(A);
        now.addAndGet(3_600_000);
        int passed = 0;
        for (int i = 0; i < 10; i++) if (guard.acquire(A) == FloodGuard.Verdict.PASS) passed++;
        assertEquals(3, passed, "uma hora parado não vira rajada de 36 mil");
    }

    @Test
    void clockGoingBackDoesNotMintTokens() {
        AtomicLong now = new AtomicLong(10_000);
        FloodGuard guard = new FloodGuard(1, 1, now::get);
        assertEquals(FloodGuard.Verdict.PASS, guard.acquire(A));
        now.set(0);
        assertEquals(FloodGuard.Verdict.DROP_NOTIFY, guard.acquire(A));
    }

    @Test
    void forgetAndIdleSweepFreeMemory() {
        AtomicLong now = new AtomicLong(0);
        FloodGuard guard = new FloodGuard(2, 1, now::get);
        guard.acquire(A);
        guard.forget(A);
        assertEquals(0, guard.size(), "logout esquece o balde");
        for (int i = 0; i < 300; i++) guard.acquire(UUID.randomUUID());
        now.addAndGet(61_000);
        guard.acquire(B);
        assertEquals(1, guard.size(), "mapa grande: os parados há um minuto saem na próxima consulta");
    }
}
