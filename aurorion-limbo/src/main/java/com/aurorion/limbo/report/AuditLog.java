package com.aurorion.limbo.report;

import com.aurorion.limbo.AurorionLimbo;
import com.aurorion.limbo.config.LimboConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * O registro permanente do que aconteceu no Limbo.
 *
 * <p>Mora em {@code <mundo>/aurorion_limbo/auditoria.jsonl}, e nao no {@code SavedData}, por tres
 * motivos que se somam:
 *
 * <ul>
 *   <li><b>Quem le nao e o jogo.</b> E a staff, e o bot do Discord. Um arquivo de texto se le com
 *       {@code tail}, se copia, se versiona e sobrevive a um rollback de save.</li>
 *   <li><b>So cresce.</b> Um {@code SavedData} e reescrito inteiro a cada gravacao; um log de
 *       acrescimo escreve so a linha nova, para sempre.</li>
 *   <li><b>Precisa sobreviver ao proprio estado.</b> Se o {@code LimboData} for perdido ou zerado, a
 *       historia de quem saiu pela Porta continua la. E essa historia que a staff usa depois.</li>
 * </ul>
 *
 * <h2>Onde a escrita acontece</h2>
 *
 * <p>Numa thread propria e unica, e nao na do servidor: abrir, acrescentar e fechar um arquivo e IO de
 * disco, e um disco lento (backup rodando, HD compartilhado) nao deve virar engasgo de tick. Uma
 * thread so mantem a ordem das linhas igual a ordem dos eventos. A linha sai da fila em milissegundos,
 * entao um servidor que caia um segundo depois <b>ja gravou</b> o acontecido; no desligamento normal
 * {@link #drain} espera a fila esvaziar, e o {@link #tail} tambem, para o {@code /limbo auditoria}
 * mostrar o que acabou de acontecer. O webhook segue a parte: rede, latencia imprevisivel, e um
 * destino que pode estar fora do ar.
 */
public final class AuditLog {
    private static final String DIR = AurorionLimbo.MOD_ID;
    private static final String FILE = "auditoria.jsonl";

    /** Quanto o servidor espera a fila de escrita antes de seguir sem ela. */
    private static final long DRAIN_TIMEOUT_SECONDS = 5;

    private AuditLog() {
    }

    /** Criada so no primeiro evento: os testes que so leem o arquivo nao sobem thread nenhuma. */
    private static final class Writer {
        private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "Aurorion Limbo auditoria");
            thread.setDaemon(true);
            return thread;
        });
    }

    public static Path path(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve(DIR).resolve(FILE);
    }

    /**
     * Grava o evento, ecoa no log se a config pedir, e empurra para o webhook se houver um.
     *
     * <p>Nunca lanca: uma falha de disco ou de rede nao pode derrubar o tick que estava aplicando a
     * regra. O pior caso e uma linha perdida com um aviso no log — a mecanica em si ja aconteceu.
     */
    public static void record(MinecraftServer server, AuditEvent event) {
        String line = event.toLine();
        // Caminho e JSON montados aqui, na thread do servidor; a fila so recebe texto pronto.
        Path file = path(server);
        String json = event.toJson().toString();
        try {
            Writer.EXECUTOR.execute(() -> append(file, json, line));
        } catch (RejectedExecutionException e) {
            append(file, json, line);
        }

        if (LimboConfig.AUDIT_TO_LOG.get()) {
            AurorionLimbo.LOGGER.info("[Limbo] {}", line);
        }

        DiscordSink.push(event);

        // Marcos publicos (queda, saida, fim) para a linha do tempo do site. No-op sem integracao.
        try {
            LimboFacts.publish(server, event);
        } catch (RuntimeException e) {
            AurorionLimbo.LOGGER.warn("Nao consegui publicar o fato do Limbo: {}", line, e);
        }
    }

    private static void append(Path file, String json, String line) {
        try {
            Files.createDirectories(file.getParent());
            Files.write(file,
                    List.of(json),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException e) {
            AurorionLimbo.LOGGER.error("Nao consegui gravar a auditoria do Limbo: {}", line, e);
        }
    }

    /**
     * Espera as linhas ja enfileiradas chegarem ao disco (no maximo {@value #DRAIN_TIMEOUT_SECONDS} s).
     * Chamado no desligamento do servidor e antes de ler o arquivo.
     */
    public static void drain() {
        try {
            Writer.EXECUTOR.submit(() -> { }).get(DRAIN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException | RejectedExecutionException e) {
            AurorionLimbo.LOGGER.warn("A fila da auditoria do Limbo nao esvaziou a tempo.", e);
        }
    }

    /**
     * As ultimas {@code limit} linhas, mais novas primeiro. Para o {@code /limbo auditoria}.
     *
     * <p>Le de tras para frente em blocos de 8 KiB. Polling por RCON custa o tamanho da resposta,
     * nao o historico inteiro da temporada.
     */
    public static List<String> tail(MinecraftServer server, int limit) {
        drain();
        Path file = path(server);
        if (!Files.isReadable(file)) {
            return List.of();
        }

        try {
            return tail(file, limit);
        } catch (IOException e) {
            AurorionLimbo.LOGGER.error("Nao consegui ler a auditoria do Limbo", e);
            return List.of();
        }
    }

    static List<String> tail(Path file, int limit) throws IOException {
        if (limit <= 0) return List.of();
        List<String> lines = new ArrayList<>(Math.min(limit, 200));
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            long position = channel.size();
            ByteBuffer buffer = ByteBuffer.allocate(8192);
            ByteArrayOutputStream reversed = new ByteArrayOutputStream(512);
            boolean lastByte = true;
            boolean skipPartial = false;
            long scanned = 0;
            while (position > 0 && lines.size() < limit && scanned < 2L * 1024 * 1024) {
                int size = (int) Math.min(position, buffer.capacity());
                position -= size;
                channel.position(position);
                buffer.clear().limit(size);
                while (buffer.hasRemaining() && channel.read(buffer) > 0) { }
                for (int i = buffer.position() - 1; i >= 0 && lines.size() < limit; i--) {
                    byte value = buffer.get(i);
                    scanned++;
                    if (lastByte) {
                        skipPartial = value != '\n';
                        lastByte = false;
                    }
                    if (value == '\n') {
                        if (!skipPartial && reversed.size() > 0) lines.add(decodeReverse(reversed));
                        skipPartial = false;
                        reversed.reset();
                    } else if (!skipPartial) {
                        if (reversed.size() >= 65536) throw new IOException("Linha de auditoria excede 64 KiB");
                        reversed.write(value);
                    }
                }
            }
            if (position == 0 && !skipPartial && reversed.size() > 0 && lines.size() < limit) {
                lines.add(decodeReverse(reversed));
            }
        }
        return lines;
    }

    private static String decodeReverse(ByteArrayOutputStream reversed) {
        byte[] bytes = reversed.toByteArray();
        for (int left = 0, right = bytes.length - 1; left < right; left++, right--) {
            byte swap = bytes[left]; bytes[left] = bytes[right]; bytes[right] = swap;
        }
        int length = bytes.length;
        if (length > 0 && bytes[length - 1] == '\r') length--;
        return new String(bytes, 0, length, StandardCharsets.UTF_8);
    }
}
