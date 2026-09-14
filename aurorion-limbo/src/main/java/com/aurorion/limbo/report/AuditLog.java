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
 * <h2>Por que a escrita e sincrona (e o webhook nao)</h2>
 *
 * <p>Sao duzentos bytes num evento que acontece algumas vezes por dia — o custo real e menor que o de
 * agendar a tarefa. Em compensacao, escrever na hora significa que um servidor que caia um segundo
 * depois <b>ja gravou</b> o acontecido. O webhook e o oposto: rede, latencia imprevisivel, e um
 * destino que pode estar fora do ar. Por isso ele sai da thread do servidor e este arquivo nao.
 */
public final class AuditLog {
    private static final String DIR = AurorionLimbo.MOD_ID;
    private static final String FILE = "auditoria.jsonl";

    private AuditLog() {
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

        try {
            Path file = path(server);
            Files.createDirectories(file.getParent());
            Files.write(file,
                    List.of(event.toJson().toString()),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException e) {
            AurorionLimbo.LOGGER.error("Nao consegui gravar a auditoria do Limbo: {}", line, e);
        }

        if (LimboConfig.AUDIT_TO_LOG.get()) {
            AurorionLimbo.LOGGER.info("[Limbo] {}", line);
        }

        DiscordSink.push(event);
    }

    /**
     * As ultimas {@code limit} linhas, mais novas primeiro. Para o {@code /limbo auditoria}.
     *
     * <p>Le de tras para frente em blocos de 8 KiB. Polling por RCON custa o tamanho da resposta,
     * nao o historico inteiro da temporada.
     */
    public static List<String> tail(MinecraftServer server, int limit) {
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
