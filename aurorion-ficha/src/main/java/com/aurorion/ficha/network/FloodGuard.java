package com.aurorion.ficha.network;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Teto de pacotes por jogador, antes de qualquer trabalho na thread do servidor.
 *
 * <p>Um balde de fichas por perfil: cheio, aguenta uma rajada de {@code capacity} pacotes; depois
 * passa um a cada {@code 1000 / perSecond} ms. É o teto de fora — o {@code FichaServer} ainda barra
 * a mesma ação repetida em 250 ms. Serve para um cliente adulterado (ou a tecla Y presa, que repete
 * pelo sistema operacional) não encher a fila do servidor de tarefas: o pacote recusado aqui nem
 * chega ao {@code enqueueWork}.
 *
 * <p>Os handlers de pacote rodam nas threads da rede (mais de uma), por isso o mapa é concorrente e
 * cada balde se tranca sozinho. Nada por tick: quem sai é esquecido no logout ({@link #forget}) e,
 * se o mapa crescer mesmo assim, os baldes parados há um minuto saem na próxima consulta.
 * Sem dependência do Minecraft (testável).
 */
public final class FloodGuard {
    /** O que fazer com o pacote. {@code DROP_NOTIFY}: recusado, e vale avisar o jogador (no máximo um aviso a cada 2 s). */
    public enum Verdict { PASS, DROP, DROP_NOTIFY }

    private static final long NOTIFY_EVERY_MS = 2_000L;
    private static final long IDLE_MS = 60_000L;
    private static final int SWEEP_ABOVE = 256;

    private final int capacity;
    private final double perMs;
    private final LongSupplier clock;
    private final Map<UUID, Bucket> buckets = new ConcurrentHashMap<>();

    private static final class Bucket {
        double tokens;
        long last;
        long notified = Long.MIN_VALUE / 2;

        Bucket(double tokens, long last) {
            this.tokens = tokens;
            this.last = last;
        }
    }

    public FloodGuard(int capacity, int perSecond, LongSupplier clock) {
        this.capacity = Math.max(1, capacity);
        this.perMs = Math.max(1, perSecond) / 1000.0;
        this.clock = clock;
    }

    public Verdict acquire(UUID player) {
        long now = clock.getAsLong();
        if (buckets.size() > SWEEP_ABOVE) sweep(now);
        Bucket bucket = buckets.computeIfAbsent(player, id -> new Bucket(capacity, now));
        synchronized (bucket) {
            bucket.tokens = Math.min(capacity, bucket.tokens + Math.max(0L, now - bucket.last) * perMs);
            bucket.last = now;
            if (bucket.tokens >= 1.0) {
                bucket.tokens -= 1.0;
                return Verdict.PASS;
            }
            if (now - bucket.notified >= NOTIFY_EVERY_MS) {
                bucket.notified = now;
                return Verdict.DROP_NOTIFY;
            }
            return Verdict.DROP;
        }
    }

    public void forget(UUID player) {
        buckets.remove(player);
    }

    public void clear() {
        buckets.clear();
    }

    public int size() {
        return buckets.size();
    }

    private void sweep(long now) {
        buckets.values().removeIf(b -> {
            synchronized (b) {
                return now - b.last > IDLE_MS;
            }
        });
    }
}
