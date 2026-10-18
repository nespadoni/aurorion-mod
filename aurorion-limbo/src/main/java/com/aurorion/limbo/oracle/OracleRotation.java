package com.aurorion.limbo.oracle;

import com.aurorion.limbo.AurorionLimbo;
import com.aurorion.limbo.config.LimboConfig;
import com.aurorion.limbo.registry.LimboEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * O sorteio diario: um lugar diferente a cada dia, entre os pontos que a staff cadastrou.
 *
 * <p>O relogio e o <b>real</b>, nao o do Minecraft. O tempo do jogo pula com cama e {@code /time},
 * entao uma rotacao presa a ele apareceria duas vezes numa noite e nenhuma em outra — e o combinado
 * com os jogadores ("ele troca meia-noite") deixaria de valer.
 *
 * <p>O dia e guardado como numero, e nao como "ja rodei hoje": um servidor que passou a madrugada
 * desligado volta, percebe que o dia mudou e sorteia na hora, em vez de esperar a proxima virada.
 */
public final class OracleRotation {
    /** Teto do intervalo entre tentativas de concluir um movimento pendente, em varreduras de 1 s. */
    private static final int MAX_PENDING_WAIT = 60;
    /** A partir de quantas falhas seguidas o log avisa a staff (uma vez). */
    private static final int WARN_AFTER_FAILURES = 10;

    /** Tentativas seguidas de concluir o movimento pendente sem achar o Oraculo. Thread do servidor. */
    private static int pendingFailures;
    /** Varreduras de 1 s que ainda faltam ate a proxima tentativa. */
    private static int pendingWait;

    private OracleRotation() {}

    /** Chamado pela varredura de 1 s do {@code LimboServerEvents}; ler o relogio uma vez por segundo
     *  e barato, e nao amarra a rotacao a nenhum resto de divisao do contador de ticks. */
    public static void tick(MinecraftServer server) {
        OracleData data = OracleData.get(server);
        // Ler entidades de um chunk e assincrono. Um movimento aceito fica persistido e e concluido
        // aqui assim que o NPC entra no indice, inclusive depois de reiniciar o servidor.
        if (data.pendingSpot() != null) {
            // Oraculo sumido de vez nao pode custar varredura de entidades e ticket de chunk a cada
            // segundo para sempre: entre uma falha e outra o intervalo dobra, ate um minuto.
            if (pendingWait > 0) {
                pendingWait--;
                return;
            }
            if (!finishPending(server, data)) {
                backOff(data);
                return;
            }
            reset();
        }
        long today = currentDay();
        if (data.rotatedDay() == today) return;
        // Primeira partida com pontos cadastrados: marca o dia sem teleportar ninguem de surpresa.
        boolean first = data.rotatedDay() < 0;
        if (first) {
            data.setRotatedDay(today);
        } else if (rotate(server, data, null) != null) {
            // Um retorno nao nulo significa movimento concluido ou garantidamente enfileirado.
            data.setRotatedDay(today);
        }
    }

    /**
     * O numero do dia corrente, ja deslocado pela hora configurada.
     *
     * <p>Com {@code horaDaRotacao=0} isto e a data civil. Com 18, o "dia do Oraculo" comeca as 18h —
     * e por isso o deslocamento e uma subtracao antes de descartar a hora.
     */
    public static long currentDay() {
        return LocalDateTime.now(ZoneId.systemDefault())
                .minusHours(LimboConfig.ORACLE_ROTATION_HOUR.get())
                .toLocalDate().toEpochDay();
    }

    /**
     * Sorteia (ou usa o ponto pedido) e leva o Oraculo para la.
     *
     * @param forced ponto escolhido pela staff, ou {@code null} para sortear
     * @return o ponto onde ele ficou, ou {@code null} se faltou ponto, Oraculo ou dimensao
     */
    @Nullable
    public static OracleData.Spot rotate(MinecraftServer server, OracleData data, @Nullable OracleData.Spot forced) {
        List<OracleData.Spot> spots = data.all();
        if (spots.isEmpty()) return null;
        OracleData.Spot target = forced != null ? forced : draw(server, data, spots);
        ServerLevel level = levelOf(server, target.dimension());
        if (level == null) {
            AurorionLimbo.LOGGER.warn("Oraculo: ponto '{}' aponta para a dimensao {}, que nao existe neste servidor.",
                    target.name(), target.dimension());
            return null;
        }
        OracleEntity oracle = findForRotation(server, data);
        if (oracle == null) {
            if (!hasStoredOracle(data)) {
                AurorionLimbo.LOGGER.warn("Oraculo: rotacao para '{}' sem nenhum Oraculo conhecido."
                        + " Invoque um com /summon aurorion_limbo:oraculo.", target.name());
                return null;
            }
            data.setPending(target.name());
            // Movimento novo: a primeira tentativa volta a ser no proximo segundo.
            reset();
            AurorionLimbo.LOGGER.info("Oraculo: movimento para '{}' aguardando o carregamento do chunk antigo.",
                    target.name());
            return target;
        }
        move(oracle, level, target, data);
        return target;
    }

    private static void move(OracleEntity oracle, ServerLevel level, OracleData.Spot target, OracleData data) {
        BlockPos pos = target.pos();
        oracle.teleportTo(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, Set.of(), target.yaw(), 0);
        data.remember(oracle);
        data.setCurrent(target.name());
        data.setPending(null);
        AurorionLimbo.LOGGER.info("Oraculo: agora em '{}' ({} {} {} em {}).", target.name(),
                pos.getX(), pos.getY(), pos.getZ(), target.dimension());
    }

    /** 2, 4, 8... varreduras ate a proxima tentativa, com teto de {@value #MAX_PENDING_WAIT}. */
    private static void backOff(OracleData data) {
        pendingFailures++;
        pendingWait = Math.min(MAX_PENDING_WAIT, 1 << Math.min(pendingFailures, 6)) - 1;
        if (pendingFailures == WARN_AFTER_FAILURES) {
            OracleData.Spot target = data.pendingSpot();
            AurorionLimbo.LOGGER.warn("Oraculo: movimento para '{}' segue sem achar o Oraculo apos {} tentativas;"
                    + " tentando a cada {} s. Se ele morreu, invoque um com /summon aurorion_limbo:oraculo.",
                    target == null ? "?" : target.name(), pendingFailures, MAX_PENDING_WAIT);
        }
    }

    /** Zera a espera entre tentativas. Tambem chamado quando o servidor para. */
    public static void reset() {
        pendingFailures = 0;
        pendingWait = 0;
    }

    /** Tenta concluir um movimento que esperava a leitura das entidades do chunk. */
    private static boolean finishPending(MinecraftServer server, OracleData data) {
        OracleData.Spot target = data.pendingSpot();
        if (target == null) {
            data.setPending(null);
            return true;
        }
        ServerLevel destination = levelOf(server, target.dimension());
        if (destination == null) return false;
        OracleEntity oracle = findForRotation(server, data);
        if (oracle == null) return false;
        move(oracle, destination, target, data);
        return true;
    }

    /** Sorteia evitando repetir o lugar de ontem, quando ha mais de um cadastrado. */
    private static OracleData.Spot draw(MinecraftServer server, OracleData data, List<OracleData.Spot> spots) {
        OracleData.Spot previous = data.currentSpot();
        List<OracleData.Spot> pool = previous == null || spots.size() <= 1 ? spots
                : spots.stream().filter(spot -> !spot.name().equals(previous.name())).toList();
        return pool.get(server.overworld().getRandom().nextInt(pool.size()));
    }

    /** O Oraculo pode estar em qualquer dimensao; o indice por tipo evita varrer todas as entidades. */
    @Nullable
    public static OracleEntity find(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            List<? extends OracleEntity> found = level.getEntities(LimboEntities.ORACLE.get(), entity -> true);
            if (!found.isEmpty()) return found.getFirst();
        }
        return null;
    }

    /**
     * A entidade persistente sai do indice de entidades quando o chunk fica longe de jogadores.
     * Na rotacao, carregamos somente o chunk conhecido do Oraculo, reencontramos pelo UUID e logo
     * o teleportamos para o novo ponto. Isso nao deixa ticket permanente nem mantem a ilha carregada.
     */
    @Nullable
    private static OracleEntity findForRotation(MinecraftServer server, OracleData data) {
        // Caminho barato primeiro: o UUID conhecido, busca em hash na dimensao gravada. A varredura
        // por tipo em todas as dimensoes fica para quando ele nao esta carregado ali.
        UUID knownId = data.oracleUuid();
        ResourceLocation knownDimension = data.oracleDimension();
        if (knownId != null && knownDimension != null) {
            ServerLevel knownLevel = levelOf(server, knownDimension);
            if (knownLevel != null && knownLevel.getEntity(knownId) instanceof OracleEntity oracle) return oracle;
        }
        OracleEntity loaded = find(server);
        if (loaded != null) return loaded;
        // Compatibilidade: saves anteriores a esta correção não têm UUID, mas já guardam o ponto
        // sorteado. Carregar esse único chunk permite reencontrar o NPC antigo sem pedir /summon.
        ResourceLocation dimension = data.oracleDimension();
        BlockPos position = data.oraclePos();
        if (data.oracleUuid() == null || dimension == null || position == null) {
            OracleData.Spot current = data.currentSpot();
            if (current == null) return null;
            dimension = current.dimension(); position = current.pos();
        }
        ServerLevel level = levelOf(server, dimension);
        if (level == null) return null;
        ChunkPos chunk = new ChunkPos(position);
        // FULL carrega os blocos, mas as entidades chegam por outra fila assincrona. O ticket de
        // portal mantem apenas este chunk acessivel por alguns segundos; ele expira sozinho.
        level.getChunkSource().addRegionTicket(TicketType.PORTAL, chunk, 3, position);
        if (!level.getChunkSource().hasChunk(chunk.x, chunk.z))
            level.getChunkSource().getChunk(chunk.x, chunk.z, ChunkStatus.FULL, true);
        if (data.oracleUuid() != null && level.getEntity(data.oracleUuid()) instanceof OracleEntity oracle) return oracle;
        BlockPos searchPosition = position;
        List<? extends OracleEntity> found = level.getEntities(LimboEntities.ORACLE.get(),
                entity -> entity.blockPosition().distSqr(searchPosition) <= 256.0D);
        if (!found.isEmpty()) return found.getFirst();
        return null;
    }

    private static boolean hasStoredOracle(OracleData data) {
        return data.oracleUuid() != null && data.oracleDimension() != null && data.oraclePos() != null
                || data.currentSpot() != null;
    }

    @Nullable
    public static ServerLevel levelOf(MinecraftServer server, ResourceLocation dimension) {
        return server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
    }

    /** Onde o Oraculo esta de fato agora, que nem sempre e o ponto sorteado (ex.: ninguem o invocou). */
    public static String describe(MinecraftServer server) {
        OracleEntity oracle = find(server);
        if (oracle == null) {
            OracleData data = OracleData.get(server);
            if (data.oracleDimension() != null && data.oraclePos() != null) {
                BlockPos pos = data.oraclePos();
                OracleData.Spot pending = data.pendingSpot();
                return String.format("descarregado em %d %d %d em %s%s", pos.getX(), pos.getY(), pos.getZ(),
                        data.oracleDimension(), pending == null ? "" : "; movimento pendente=" + pending.name());
            }
            return "nenhum Oraculo no mundo";
        }
        Level level = oracle.level();
        return String.format("%d %d %d em %s", oracle.getBlockX(), oracle.getBlockY(), oracle.getBlockZ(),
                level.dimension().location());
    }
}
