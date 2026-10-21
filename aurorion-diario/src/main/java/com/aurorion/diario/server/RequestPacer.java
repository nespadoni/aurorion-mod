package com.aurorion.diario.server;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * Ritmo das chamadas que um jogador provoca no site: no maximo uma por intervalo, por balde.
 *
 * <h2>Adiar, nao recusar</h2>
 *
 * <p>Recusar em silencio quebrava a tela: o salvamento automatico (2 s depois da ultima tecla) ou o
 * clique em outra entrada simplesmente sumiam, e a tela ficava em "Salvando…" ou "Abrindo…". Aqui o
 * pedido que chega cedo demais <b>espera a vez</b>, e um pedido novo para o mesmo lugar ({@code slot})
 * toma o lugar do que esperava — so a ultima versao do rascunho interessa, e so a ultima entrada
 * clicada. Cada balde tem poucos lugares; passou disso, a recusa e explicita ({@link Result#REFUSED})
 * e quem chama responde ao cliente.
 *
 * <h2>Sair nao zera</h2>
 *
 * <p>O intervalo e do perfil, e sobrevive ao logout: reconectar nao compra uma chamada nova. O que
 * esperava a vez de um jogador que saiu roda na hora ({@link #flush}) — um rascunho na fila nao pode
 * se perder porque a pessoa fechou o jogo.
 *
 * <p>So a thread do servidor mexe aqui; o {@link Scheduler} devolve o disparo para ela. Sem
 * dependencia do Minecraft: testavel com relogio e agenda falsos.
 */
final class RequestPacer {
    enum Result { RAN, DEFERRED, COALESCED, REFUSED }

    /** Agenda {@code task} para daqui a {@code delayMillis}, na thread do servidor. */
    interface Scheduler {
        /** @return false se nao ha como agendar (desligando); o pacer entao roda na hora. */
        boolean later(Runnable task, long delayMillis);
    }

    private final long intervalMillis;
    private final int slotsPerBucket;
    private final LongSupplier clock;
    private final Scheduler scheduler;
    private final Map<String, Long> nextAllowed = new HashMap<>();
    private final Map<String, LinkedHashMap<String, Runnable>> waiting = new HashMap<>();

    RequestPacer(long intervalMillis, int slotsPerBucket, LongSupplier clock, Scheduler scheduler) {
        if (intervalMillis <= 0 || slotsPerBucket <= 0) throw new IllegalArgumentException("Invalid pacing");
        this.intervalMillis = intervalMillis;
        this.slotsPerBucket = slotsPerBucket;
        this.clock = clock;
        this.scheduler = scheduler;
    }

    Result submit(String bucket, String slot, Runnable task) {
        long now = clock.getAsLong();
        LinkedHashMap<String, Runnable> queue = waiting.get(bucket);
        if (queue != null && queue.containsKey(slot)) {
            queue.put(slot, task);
            return Result.COALESCED;
        }
        long allowed = nextAllowed.getOrDefault(bucket, Long.MIN_VALUE);
        if ((queue == null || queue.isEmpty()) && now >= allowed) {
            mark(bucket, now);
            task.run();
            return Result.RAN;
        }
        if (queue == null) {
            queue = new LinkedHashMap<>();
            waiting.put(bucket, queue);
        }
        if (queue.size() >= slotsPerBucket) return Result.REFUSED;
        boolean first = queue.isEmpty();
        queue.put(slot, task);
        if (first) schedule(bucket, Math.max(0L, allowed - now));
        return Result.DEFERRED;
    }

    /** Roda ja tudo que esperava nos baldes que comecam com {@code prefix} (o jogador saiu). */
    void flush(String prefix) {
        long now = clock.getAsLong();
        for (Iterator<Map.Entry<String, LinkedHashMap<String, Runnable>>> it = waiting.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, LinkedHashMap<String, Runnable>> entry = it.next();
            if (!entry.getKey().startsWith(prefix)) continue;
            it.remove();
            mark(entry.getKey(), now);
            for (Runnable task : entry.getValue().values()) task.run();
        }
    }

    void clear() {
        nextAllowed.clear();
        waiting.clear();
    }

    int waitingCount() {
        int total = 0;
        for (LinkedHashMap<String, Runnable> queue : waiting.values()) total += queue.size();
        return total;
    }

    private void fire(String bucket) {
        LinkedHashMap<String, Runnable> queue = waiting.get(bucket);
        if (queue == null || queue.isEmpty()) return; // ja rodou no flush
        long now = clock.getAsLong();
        long allowed = nextAllowed.getOrDefault(bucket, Long.MIN_VALUE);
        if (now < allowed) { // relogio da agenda adiantado: espera o resto
            schedule(bucket, allowed - now);
            return;
        }
        Iterator<Runnable> it = queue.values().iterator();
        Runnable task = it.next();
        it.remove();
        if (queue.isEmpty()) waiting.remove(bucket);
        else schedule(bucket, intervalMillis);
        mark(bucket, now);
        task.run();
    }

    private void schedule(String bucket, long delay) {
        if (!scheduler.later(() -> fire(bucket), delay)) {
            // Sem agenda (servidor desligando): roda agora em vez de perder o que esperava.
            LinkedHashMap<String, Runnable> queue = waiting.remove(bucket);
            if (queue != null) queue.values().forEach(Runnable::run);
        }
    }

    private void mark(String bucket, long now) {
        nextAllowed.put(bucket, now + intervalMillis);
        // Limpa de vez em quando quem ja pode chamar de novo: o mapa nao cresce com a populacao.
        if (nextAllowed.size() > 4096) nextAllowed.values().removeIf(at -> at <= now);
    }
}
