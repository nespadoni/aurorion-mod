package com.aurorion.integracao.outbox;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.LongSupplier;

/**
 * A caixa de saida dos fatos do jogo: aceita na hora, grava em disco, entrega quando der.
 *
 * <h2>Por que assim</h2>
 *
 * <ul>
 *   <li><b>A thread do servidor so enfileira.</b> {@link #offer} e um {@code offer} numa fila
 *       limitada — nunca bloqueia, nunca toca disco nem rede. Fila cheia descarta com aviso: o jogo
 *       vale mais que o registro.</li>
 *   <li><b>Disco antes da rede.</b> A thread da caixa grava cada fato no spool
 *       ({@code <mundo>/aurorion_integracao/pendentes.jsonl}) <em>antes</em> de tentar enviar. Site
 *       fora do ar, reinicio do servidor ou queda no meio do envio: o fato continua no arquivo e sai
 *       na proxima tentativa. O backend deduplica pelo {@code event_id}, entao reenviar e seguro.</li>
 *   <li><b>So sai do spool o que o backend respondeu.</b> Aceito, duplicado ou rejeitado encerram o
 *       fato; timeout e erro de rede mantem o lote para depois, com espera crescente.</li>
 *   <li><b>Sem trabalho quando nada acontece.</b> A thread dorme na fila; nao ha polling do jogo nem
 *       timer por tick.</li>
 * </ul>
 *
 * <p>Classe sem nenhuma dependencia do Minecraft de proposito: e testavel com um arquivo temporario
 * e um transporte falso.
 */
public final class Outbox implements AutoCloseable {
    /** Avisos para o log do servidor. Implementado por quem cria a caixa. */
    public interface Log {
        void info(String message);

        void warn(String message);
    }

    static final int MAX_BATCH_ITEMS = 50;
    static final int MAX_BATCH_BYTES = 200 * 1024;
    static final int MAX_LINE_BYTES = 16 * 1024;
    private static final int QUEUE_CAPACITY = 2048;
    private static final long MIN_BACKOFF = 2_000L;
    private static final long MAX_BACKOFF = 5 * 60_000L;
    private static final long FORBIDDEN_WAIT = 10 * 60_000L;
    private static final long IDLE_WAIT = 30_000L;
    private static final long WARN_EVERY = 60_000L;

    private final BlockingQueue<String> queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
    private final ArrayDeque<String> pending = new ArrayDeque<>();
    private final Path spool;
    private final long maxSpoolBytes;
    private final Function<List<String>, Attempt> transport;
    private final Log log;
    private final LongSupplier clock;
    private final Thread worker;

    private volatile boolean running = true;
    private long pendingBytes;
    private long backoff = MIN_BACKOFF;
    private long nextAttemptAt;
    private long droppedSinceWarn;
    private long lastDropWarn;
    private boolean forbiddenWarned;

    public Outbox(Path spool, long maxSpoolBytes, Function<List<String>, Attempt> transport, Log log) {
        this(spool, maxSpoolBytes, transport, log, System::currentTimeMillis, true);
    }

    /** Visivel para teste: relogio controlado e sem thread (o teste chama {@link #runOnce}). */
    Outbox(Path spool, long maxSpoolBytes, Function<List<String>, Attempt> transport, Log log,
           LongSupplier clock, boolean startThread) {
        this.spool = spool;
        this.maxSpoolBytes = maxSpoolBytes;
        this.transport = transport;
        this.log = log;
        this.clock = clock;
        loadSpool();
        this.worker = new Thread(this::loop, "aurorion-integracao");
        this.worker.setDaemon(true);
        if (startThread) this.worker.start();
    }

    /**
     * Enfileira um fato ja serializado. Seguro para chamar da thread do servidor: nunca bloqueia.
     *
     * @return {@code false} se a caixa esta fechando ou a fila estourou (o fato foi descartado).
     */
    public boolean offer(String json) {
        if (!running) return false;
        if (queue.offer(json)) return true;
        noteDrop();
        return false;
    }

    /** Quantos fatos aguardam entrega (fila + spool). Para diagnostico. */
    public int backlog() {
        return queue.size() + pendingSize();
    }

    private synchronized int pendingSize() {
        return pending.size();
    }

    /** Para a thread. O que estava na fila vai para o spool e sai no proximo boot. */
    @Override
    public void close() {
        running = false;
        worker.interrupt();
        try {
            worker.join(3_000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ── Thread da caixa ──────────────────────────────────────────────────────────────────────

    private void loop() {
        while (running) {
            try {
                runOnce(waitMillis());
            } catch (InterruptedException e) {
                break;
            } catch (RuntimeException e) {
                log.warn("Integracao: erro inesperado na caixa de saida (" + e.getClass().getSimpleName() + "); tentando de novo");
                nextAttemptAt = clock.getAsLong() + MAX_BACKOFF;
            }
        }
        // Fechando: tudo que ainda estava na fila vai para o disco, para o proximo boot. A flag de
        // interrupcao sai antes: escrita NIO com a thread interrompida fecha o canal e perderia isto.
        Thread.interrupted();
        List<String> rest = new ArrayList<>();
        queue.drainTo(rest);
        persist(rest);
    }

    private long waitMillis() {
        if (pendingSize() == 0) return IDLE_WAIT;
        return Math.clamp(nextAttemptAt - clock.getAsLong(), 0L, IDLE_WAIT);
    }

    /** Um ciclo: espera um fato (ou a hora de reenviar), grava no spool e tenta entregar. */
    void runOnce(long waitMillis) throws InterruptedException {
        String first = waitMillis > 0 ? queue.poll(waitMillis, TimeUnit.MILLISECONDS) : queue.poll();
        List<String> incoming = new ArrayList<>();
        if (first != null) incoming.add(first);
        queue.drainTo(incoming);
        persist(incoming);

        while (pendingSize() > 0 && clock.getAsLong() >= nextAttemptAt) {
            if (!sendBatch()) break;
        }
    }

    /** @return {@code true} se vale tentar o proximo lote ja (o anterior foi entregue). */
    private boolean sendBatch() {
        List<String> batch = nextBatch();
        Attempt attempt = transport.apply(batch);
        switch (attempt.kind()) {
            case DELIVERED -> {
                int done = resolve(batch, attempt.resolved());
                if (!attempt.rejections().isEmpty()) {
                    log.warn("Integracao: o site recusou " + attempt.rejections().size() + " fato(s): " + attempt.rejections());
                }
                if (forbiddenWarned) {
                    log.info("Integracao: credencial aceita de novo; entrega retomada");
                    forbiddenWarned = false;
                }
                backoff = MIN_BACKOFF;
                if (done < batch.size()) {
                    // Resposta parcial: o que ficou sem resultado volta depois, com espera.
                    scheduleRetry(0);
                    return false;
                }
                return true;
            }
            case FORBIDDEN -> {
                if (!forbiddenWarned) {
                    log.warn("Integracao: o site recusou a credencial (" + attempt.detail()
                            + "). Confira integracao.token; os fatos ficam guardados ate la.");
                    forbiddenWarned = true;
                }
                nextAttemptAt = clock.getAsLong() + FORBIDDEN_WAIT;
                return false;
            }
            default -> {
                scheduleRetry(attempt.waitMillis());
                return false;
            }
        }
    }

    private void scheduleRetry(long requested) {
        long wait = requested > 0 ? requested : backoff + ThreadLocalRandom.current().nextLong(backoff / 2 + 1);
        nextAttemptAt = clock.getAsLong() + wait;
        backoff = Math.min(MAX_BACKOFF, backoff * 2);
    }

    private synchronized List<String> nextBatch() {
        List<String> batch = new ArrayList<>(Math.min(MAX_BATCH_ITEMS, pending.size()));
        long bytes = 0;
        for (String line : pending) {
            long size = line.getBytes(StandardCharsets.UTF_8).length + 1L;
            if (batch.size() == MAX_BATCH_ITEMS || (!batch.isEmpty() && bytes + size > MAX_BATCH_BYTES)) break;
            batch.add(line);
            bytes += size;
        }
        return batch;
    }

    /** Tira do spool os fatos com resposta final. O lote e sempre o comeco da fila pendente. */
    private int resolve(List<String> batch, boolean[] resolved) {
        int done = 0;
        synchronized (this) {
            Iterator<String> it = pending.iterator();
            for (int i = 0; i < batch.size() && it.hasNext(); i++) {
                String line = it.next();
                if (i < resolved.length && resolved[i]) {
                    it.remove();
                    pendingBytes -= line.getBytes(StandardCharsets.UTF_8).length + 1L;
                    done++;
                }
            }
        }
        if (done > 0) rewriteSpool();
        return done;
    }

    // ── Spool em disco ───────────────────────────────────────────────────────────────────────

    private void loadSpool() {
        if (!Files.isRegularFile(spool)) return;
        try {
            List<String> lines = Files.readAllLines(spool, StandardCharsets.UTF_8);
            synchronized (this) {
                for (String line : lines) {
                    if (line.isBlank()) continue;
                    pending.add(line);
                    pendingBytes += line.getBytes(StandardCharsets.UTF_8).length + 1L;
                }
            }
            if (!lines.isEmpty()) log.info("Integracao: " + pendingSize() + " fato(s) pendente(s) de antes do reinicio");
        } catch (IOException e) {
            log.warn("Integracao: nao consegui ler o spool " + spool.getFileName() + " (" + e.getClass().getSimpleName() + ")");
        }
    }

    /** Acrescenta ao spool (memoria + disco). O que nao couber no limite e descartado com aviso. */
    private void persist(List<String> lines) {
        if (lines.isEmpty()) return;
        List<String> accepted = new ArrayList<>(lines.size());
        synchronized (this) {
            for (String line : lines) {
                long size = line.getBytes(StandardCharsets.UTF_8).length + 1L;
                if (size > MAX_LINE_BYTES || line.indexOf('\n') >= 0 || pendingBytes + size > maxSpoolBytes) {
                    noteDrop();
                    continue;
                }
                pending.add(line);
                pendingBytes += size;
                accepted.add(line);
            }
        }
        if (accepted.isEmpty()) return;
        try {
            Files.createDirectories(spool.getParent());
            Files.write(spool, accepted, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            // Continua em memoria e sai normalmente; so nao sobrevive a um reinicio antes da entrega.
            log.warn("Integracao: nao consegui gravar o spool (" + e.getClass().getSimpleName() + "); fatos mantidos em memoria");
        }
    }

    /** Regrava o spool com o que ainda falta, de forma atomica quando o sistema permite. */
    private void rewriteSpool() {
        List<String> snapshot;
        synchronized (this) {
            snapshot = new ArrayList<>(pending);
        }
        try {
            if (snapshot.isEmpty()) {
                Files.deleteIfExists(spool);
                return;
            }
            Path temp = spool.resolveSibling(spool.getFileName() + ".tmp");
            Files.write(temp, snapshot, StandardCharsets.UTF_8);
            try {
                Files.move(temp, spool, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, spool, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            // O arquivo antigo continua la: no pior caso, fatos ja entregues saem de novo e o
            // backend os reconhece como duplicados.
            log.warn("Integracao: nao consegui atualizar o spool (" + e.getClass().getSimpleName() + ")");
        }
    }

    private void noteDrop() {
        long now = clock.getAsLong();
        synchronized (this) {
            droppedSinceWarn++;
            if (now - lastDropWarn < WARN_EVERY) return;
            lastDropWarn = now;
            log.warn("Integracao: " + droppedSinceWarn + " fato(s) descartado(s) — fila ou spool cheios");
            droppedSinceWarn = 0;
        }
    }
}
