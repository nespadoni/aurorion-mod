package com.aurorion.integracao.outbox;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
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
 * <h2>Uma linha ruim nao trava a caixa</h2>
 *
 * <p>O corpo do lote e montado juntando as linhas ({@link IngestClient#body}), entao uma linha que nao
 * e um objeto JSON — cortada por uma queda no meio da gravacao, colada na seguinte por um append
 * depois dessa queda, editada a mao — fazia o backend recusar o lote inteiro, e o lote voltava para
 * sempre. Agora:
 * <ul>
 *   <li>toda linha e conferida ({@link #validLine}) ao entrar e ao ser lida do disco; a que nao passa
 *       vai para a quarentena ({@code rejeitados.jsonl}, ao lado do spool), nunca para o lote;</li>
 *   <li>antes de acrescentar, o fim do arquivo ganha a quebra de linha que uma gravacao interrompida
 *       deixou faltando;</li>
 *   <li>lote recusado inteiro (400/413/422) e dividido ao meio ate isolar o fato que o site nao aceita,
 *       e so esse vai para a quarentena. Se o site recusar tudo (varios fatos isolados seguidos), a
 *       caixa para de por fatos em quarentena e volta a esperar, como numa queda.</li>
 * </ul>
 *
 * <h2>Regravar sem custo quadratico</h2>
 *
 * <p>Tirar do arquivo o que foi entregue exige regrava-lo. Regravar a cada lote custava o spool
 * inteiro por lote: um acumulo de dezenas de milhares de fatos virava gigabytes escritos. Agora o
 * arquivo so e regravado quando o que ja foi entregue pesa pelo menos tanto quanto o que falta —
 * custo total linear. Ate la, o arquivo pode conter fatos ja entregues; depois de um reinicio eles saem
 * de novo e o backend os reconhece como duplicados.
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
    static final String REJECTED_FILE = "rejeitados.jsonl";
    /** Teto da quarentena: ela existe para a equipe olhar, nao para crescer sem fim. */
    static final long MAX_REJECTED_BYTES = 4L * 1024 * 1024;
    /** Fatos isolados e recusados em sequencia antes de concluir que o problema e o site. */
    static final int MAX_CONSECUTIVE_QUARANTINES = 3;
    private static final int QUEUE_CAPACITY = 2048;
    private static final long MIN_BACKOFF = 2_000L;
    private static final long MAX_BACKOFF = 5 * 60_000L;
    private static final long FORBIDDEN_WAIT = 10 * 60_000L;
    private static final long IDLE_WAIT = 30_000L;
    private static final long WARN_EVERY = 60_000L;

    private final BlockingQueue<String> queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
    private final ArrayDeque<String> pending = new ArrayDeque<>();
    private final Path spool;
    private final Path rejected;
    private final long maxSpoolBytes;
    private final Function<List<String>, Attempt> transport;
    private final Log log;
    private final LongSupplier clock;
    private final Thread worker;

    private volatile boolean running = true;
    private long pendingBytes;
    /** Bytes de fatos ja resolvidos que ainda estao no arquivo, esperando a proxima regravacao. */
    private long staleBytes;
    private long backoff = MIN_BACKOFF;
    private volatile long nextAttemptAt;
    private long droppedSinceWarn;
    private long lastDropWarn;
    private boolean forbiddenWarned;
    private long droppedTotal;
    private volatile long resolvedTotal;
    private volatile String lastResult = "nenhuma tentativa";
    private volatile long lastAttemptAt;
    private volatile long lastSuccessAt;
    private volatile boolean diskHealthy = true;
    /** Tamanho do proximo lote. Cai pela metade quando o site recusa o lote inteiro. */
    private int batchLimit = MAX_BATCH_ITEMS;
    /** Fatos do lote recusado que ainda nao foram reenviados aos pedacos. */
    private int suspects;
    private int consecutiveQuarantines;
    private long quarantinedTotal;
    private boolean rejectedFullWarned;

    public record Diagnostics(int queued, int queueCapacity, int pending, long pendingBytes,
                              long spoolCapacityBytes, long retryInMillis, long resolved, long dropped,
                              String lastResult, long lastAttemptAt, long lastSuccessAt, boolean diskHealthy) { }

    /** Counters only: never reads the spool, serializes facts or starts a request. */
    public synchronized Diagnostics diagnostics() {
        return new Diagnostics(queue.size(), QUEUE_CAPACITY, pending.size(), pendingBytes, maxSpoolBytes,
                pending.isEmpty() ? 0L : Math.max(0L, nextAttemptAt - clock.getAsLong()), resolvedTotal,
                droppedTotal, lastResult, lastAttemptAt, lastSuccessAt, diskHealthy);
    }

    /** Fatos postos em quarentena desde o inicio. Para diagnostico e teste. */
    public synchronized long quarantined() {
        return quarantinedTotal;
    }

    public Outbox(Path spool, long maxSpoolBytes, Function<List<String>, Attempt> transport, Log log) {
        this(spool, maxSpoolBytes, transport, log, System::currentTimeMillis, true);
    }

    /** Visivel para teste: relogio controlado e sem thread (o teste chama {@link #runOnce}). */
    Outbox(Path spool, long maxSpoolBytes, Function<List<String>, Attempt> transport, Log log,
           LongSupplier clock, boolean startThread) {
        this.spool = spool;
        this.rejected = spool.resolveSibling(REJECTED_FILE);
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

    /**
     * Uma linha que pode ir no lote: um objeto JSON, numa linha so, dentro do teto. E o que torna
     * seguro montar o corpo juntando texto ({@link IngestClient#body}).
     */
    static boolean validLine(String line) {
        if (line == null || line.isBlank() || line.indexOf('\n') >= 0 || line.indexOf('\r') >= 0) return false;
        if (utf8Length(line) > MAX_LINE_BYTES) return false;
        try {
            JsonElement element = JsonParser.parseString(line);
            return element.isJsonObject();
        } catch (RuntimeException | StackOverflowError e) {
            return false;
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
                lastResult = "erro interno; reenvio adiado";
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
        lastAttemptAt = clock.getAsLong();
        Attempt attempt = transport.apply(batch);
        switch (attempt.kind()) {
            case DELIVERED -> {
                int done = resolve(batch, attempt.resolved());
                resolvedTotal += done;
                lastResult = done == batch.size() ? "lote confirmado" : "resposta parcial";
                if (done > 0) {
                    lastSuccessAt = clock.getAsLong();
                    consecutiveQuarantines = 0;
                }
                if (suspects > 0) {
                    // Os pedacos do lote recusado que passaram. Acabados os suspeitos, lote cheio de novo.
                    suspects -= batch.size();
                    if (suspects <= 0) {
                        suspects = 0;
                        batchLimit = MAX_BATCH_ITEMS;
                    }
                }
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
                lastResult = "credencial recusada";
                if (!forbiddenWarned) {
                    log.warn("Integracao: o site recusou a credencial (" + attempt.detail()
                            + "). Confira integracao.token; os fatos ficam guardados ate la.");
                    forbiddenWarned = true;
                }
                nextAttemptAt = clock.getAsLong() + FORBIDDEN_WAIT;
                return false;
            }
            case MALFORMED -> {
                return isolate(batch, attempt.detail());
            }
            default -> {
                lastResult = "falha de entrega; aguardando reenvio";
                scheduleRetry(attempt.waitMillis());
                return false;
            }
        }
    }

    /**
     * O site recusou o lote inteiro pelo conteudo. Divide ao meio e tenta de novo na hora; com um fato
     * so, ele e o culpado e vai para a quarentena. Cada passo ou encolhe o lote ou tira um fato, entao
     * a busca termina — e, com {@link #MAX_CONSECUTIVE_QUARANTINES} fatos isolados seguidos sem nada
     * entregue no meio, o problema e o site, e a caixa volta a esperar em vez de esvaziar o spool.
     */
    private boolean isolate(List<String> batch, String detail) {
        if (batch.size() > 1) {
            if (suspects <= 0) suspects = batch.size();
            batchLimit = Math.max(1, batch.size() / 2);
            lastResult = "lote recusado (" + detail + "); dividindo para achar o fato";
            return true;
        }
        if (consecutiveQuarantines >= MAX_CONSECUTIVE_QUARANTINES) {
            lastResult = "o site recusa todo fato (" + detail + "); aguardando";
            if (backoff == MIN_BACKOFF) {
                log.warn("Integracao: o site recusou " + MAX_CONSECUTIVE_QUARANTINES + " fatos isolados seguidos ("
                        + detail + "); parece problema no site, nao nos fatos. Os pendentes ficam guardados.");
            }
            scheduleRetry(0);
            return false;
        }
        String line = batch.get(0);
        synchronized (this) {
            if (!line.equals(pending.peekFirst())) return false;
            pending.pollFirst();
            long size = utf8Length(line) + 1L;
            pendingBytes -= size;
            staleBytes += size;
        }
        quarantine(List.of(line), "recusado pelo site (" + detail + ")");
        consecutiveQuarantines++;
        batchLimit = MAX_BATCH_ITEMS;
        suspects = 0;
        lastResult = "fato recusado pelo site posto em quarentena";
        maybeRewriteSpool();
        return true;
    }

    private void scheduleRetry(long requested) {
        long wait = requested > 0 ? requested : backoff + ThreadLocalRandom.current().nextLong(backoff / 2 + 1);
        nextAttemptAt = clock.getAsLong() + wait;
        backoff = Math.min(MAX_BACKOFF, backoff * 2);
    }

    private synchronized List<String> nextBatch() {
        int limit = Math.clamp(batchLimit, 1, MAX_BATCH_ITEMS);
        List<String> batch = new ArrayList<>(Math.min(limit, pending.size()));
        long bytes = 0;
        for (String line : pending) {
            long size = utf8Length(line) + 1L;
            if (batch.size() == limit || (!batch.isEmpty() && bytes + size > MAX_BATCH_BYTES)) break;
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
                    long size = utf8Length(line) + 1L;
                    pendingBytes -= size;
                    staleBytes += size;
                    done++;
                }
            }
        }
        if (done > 0) maybeRewriteSpool();
        return done;
    }

    // ── Spool em disco ───────────────────────────────────────────────────────────────────────

    /**
     * Le o spool linha a linha, conferindo cada uma.
     *
     * <p>Linha invalida (JSON cortado, duas coladas, lixo) e linha grande demais vao para a
     * quarentena; o que passar de {@code maxSpoolBytes} tambem — o teto vale para o arquivo que ja
     * existia, nao so para o que chega. Bytes que nao sao UTF-8 valido viram o caractere de
     * substituicao em vez de derrubar a leitura inteira (o que antes zerava a memoria e deixava a
     * regravacao seguinte apagar o arquivo). Se algo foi separado, o arquivo e regravado limpo.
     */
    private void loadSpool() {
        if (!Files.isRegularFile(spool)) return;
        List<String> bad = new ArrayList<>();
        int oversized = 0;
        int overflow = 0;
        boolean dirty = false;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(Files.newInputStream(spool), StandardCharsets.UTF_8))) {
            StringBuilder line = new StringBuilder();
            boolean tooLong = false;
            int c;
            while (true) {
                c = reader.read();
                if (c == '\n' || c == -1) {
                    if (c == -1 && line.isEmpty() && !tooLong) break;
                    if (c == -1) dirty = true; // ultima linha sem quebra: gravacao interrompida
                    String text = line.toString();
                    if (text.endsWith("\r")) text = text.substring(0, text.length() - 1);
                    line.setLength(0);
                    if (tooLong) {
                        oversized++;
                        dirty = true;
                    } else if (!text.isBlank()) {
                        if (!validLine(text)) {
                            bad.add(text);
                            dirty = true;
                        } else {
                            long size = utf8Length(text) + 1L;
                            synchronized (this) {
                                if (pendingBytes + size > maxSpoolBytes) {
                                    bad.add(text);
                                    overflow++;
                                    dirty = true;
                                } else {
                                    pending.add(text);
                                    pendingBytes += size;
                                }
                            }
                        }
                    }
                    tooLong = false;
                    if (c == -1) break;
                    continue;
                }
                if (tooLong) continue;
                line.append((char) c);
                // Caractere nunca ocupa menos de um byte: mais caracteres que o teto ja passou dele.
                if (line.length() > MAX_LINE_BYTES) {
                    tooLong = true;
                    line.setLength(0);
                }
            }
        } catch (IOException e) {
            diskHealthy = false;
            log.warn("Integracao: nao consegui ler o spool " + spool.getFileName() + " (" + e.getClass().getSimpleName() + ")");
            return;
        }
        if (!bad.isEmpty() || oversized > 0) {
            // Sem a quarentena gravada, regravar o spool perderia as linhas: o arquivo fica como esta
            // (elas sao filtradas de novo a cada leitura e nunca entram num lote).
            if (!quarantine(bad, "invalido ou acima do teto ao ler o spool")) dirty = false;
            log.warn("Integracao: " + (bad.size() - overflow) + " linha(s) invalida(s), " + oversized
                    + " grande(s) demais e " + overflow + " acima do teto do spool separada(s) de "
                    + spool.getFileName() + (bad.isEmpty() ? "" : " para " + REJECTED_FILE));
        }
        int loaded = pendingSize();
        if (loaded > 0) log.info("Integracao: " + loaded + " fato(s) pendente(s) de antes do reinicio");
        if (dirty) rewriteSpool();
    }

    /** Acrescenta ao spool (memoria + disco). O que nao couber no limite e descartado com aviso. */
    private void persist(List<String> lines) {
        if (lines.isEmpty()) return;
        List<String> accepted = new ArrayList<>(lines.size());
        List<String> invalid = new ArrayList<>();
        for (String line : lines) {
            if (!validLine(line)) {
                if (line != null && !line.isBlank() && line.indexOf('\n') < 0 && line.indexOf('\r') < 0
                        && utf8Length(line) <= MAX_LINE_BYTES) invalid.add(line);
                else noteDrop();
                continue;
            }
            long size = utf8Length(line) + 1L;
            synchronized (this) {
                if (pendingBytes + size > maxSpoolBytes) {
                    noteDrop();
                    continue;
                }
                pending.add(line);
                pendingBytes += size;
            }
            accepted.add(line);
        }
        if (!invalid.isEmpty()) quarantine(invalid, "fato mal formado");
        if (accepted.isEmpty()) return;
        try {
            Files.createDirectories(spool.getParent());
            endWithNewline(spool);
            Files.write(spool, accepted, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            diskHealthy = true;
        } catch (IOException e) {
            diskHealthy = false;
            // Continua em memoria e sai normalmente; so nao sobrevive a um reinicio antes da entrega.
            log.warn("Integracao: nao consegui gravar o spool (" + e.getClass().getSimpleName() + "); fatos mantidos em memoria");
        }
    }

    /**
     * Uma gravacao interrompida (queda, disco cheio) pode deixar a ultima linha sem a quebra; o append
     * seguinte colaria o fato novo nela e estragaria os dois. Custa ler um byte.
     */
    private static void endWithNewline(Path file) throws IOException {
        if (!Files.isRegularFile(file)) return;
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            long size = channel.size();
            if (size == 0) return;
            ByteBuffer last = ByteBuffer.allocate(1);
            channel.read(last, size - 1);
            if (last.get(0) != '\n') channel.write(ByteBuffer.wrap(new byte[]{'\n'}), size);
        }
    }

    /** Regrava so quando o que ja saiu pesa ao menos tanto quanto o que falta: custo total linear. */
    private void maybeRewriteSpool() {
        boolean due;
        synchronized (this) {
            due = staleBytes > 0 && staleBytes >= pendingBytes;
        }
        if (due) rewriteSpool();
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
            } else {
                Files.createDirectories(spool.getParent());
                Path temp = spool.resolveSibling(spool.getFileName() + ".tmp");
                Files.write(temp, snapshot, StandardCharsets.UTF_8);
                try {
                    Files.move(temp, spool, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(temp, spool, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            synchronized (this) {
                staleBytes = 0;
            }
            diskHealthy = true;
        } catch (IOException e) {
            diskHealthy = false;
            // O arquivo antigo continua la: no pior caso, fatos ja entregues saem de novo e o
            // backend os reconhece como duplicados.
            log.warn("Integracao: nao consegui atualizar o spool (" + e.getClass().getSimpleName() + ")");
        }
    }

    /**
     * Separa linhas que nunca vao passar. Ficam em {@code rejeitados.jsonl} para a equipe olhar; o
     * arquivo tem teto e, cheio, o que vier depois e so contado no log.
     *
     * @return false se nem a quarentena pode ser gravada
     */
    private boolean quarantine(List<String> lines, String reason) {
        synchronized (this) {
            quarantinedTotal += lines.size();
        }
        if (lines.isEmpty()) return true;
        List<String> kept = new ArrayList<>(lines.size());
        try {
            Files.createDirectories(rejected.getParent());
            long size = Files.isRegularFile(rejected) ? Files.size(rejected) : 0L;
            for (String line : lines) {
                long bytes = utf8Length(line) + 1L;
                if (size + bytes > MAX_REJECTED_BYTES) break;
                kept.add(line);
                size += bytes;
            }
            if (!kept.isEmpty()) {
                endWithNewline(rejected);
                Files.write(rejected, kept, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            }
        } catch (IOException e) {
            log.warn("Integracao: nao consegui gravar a quarentena " + REJECTED_FILE + " (" + e.getClass().getSimpleName() + ")");
            return false;
        }
        log.warn("Integracao: " + lines.size() + " fato(s) em quarentena: " + reason);
        if (kept.size() < lines.size() && !rejectedFullWarned) {
            rejectedFullWarned = true;
            log.warn("Integracao: " + REJECTED_FILE + " chegou ao teto; fatos em quarentena alem dele sao descartados");
        }
        return true;
    }

    private static long utf8Length(String text) {
        long bytes = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < 0x80) bytes++;
            else if (c < 0x800) bytes += 2;
            else if (Character.isHighSurrogate(c) && i + 1 < text.length() && Character.isLowSurrogate(text.charAt(i + 1))) {
                bytes += 4;
                i++;
            } else bytes += 3;
        }
        return bytes;
    }

    private void noteDrop() {
        long now = clock.getAsLong();
        synchronized (this) {
            droppedTotal++;
            droppedSinceWarn++;
            if (now - lastDropWarn < WARN_EVERY) return;
            lastDropWarn = now;
            log.warn("Integracao: " + droppedSinceWarn + " fato(s) descartado(s) — fila ou spool cheios");
            droppedSinceWarn = 0;
        }
    }
}
