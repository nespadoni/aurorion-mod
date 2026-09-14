package com.aurorion.limbo.exile;

import com.aurorion.core.level.SafeSpot;
import com.aurorion.limbo.AurorionLimbo;
import com.aurorion.limbo.config.LimboConfig;
import com.aurorion.limbo.narrate.LimboNarrator;
import com.aurorion.limbo.network.LimboNetwork;
import com.aurorion.limbo.report.AuditEvent;
import com.aurorion.limbo.report.AuditLog;
import com.aurorion.vidas.lives.ExileSpot;
import com.aurorion.vidas.lives.LivesData;
import com.aurorion.vidas.lives.LivesManager;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * As regras do Limbo: quem entra, quanto tempo tem, e como sai.
 *
 * <h2>Quem manda em que</h2>
 *
 * <p>Este mod <b>nao decide quem esta exilado</b> — isso e do {@code aurorion_vidas}, e continua
 * sendo. Aqui a pergunta e sempre feita a ele ({@code LivesManager.isExiled}), nunca respondida por
 * um estado proprio. Uma segunda fonte de verdade sobre "esta exilado?" seria a garantia de que as
 * duas discordariam um dia, no pior momento possivel.
 *
 * <p>A consequencia pratica e boa: {@code /vidas dar} continua funcionando como resgate sem saber que
 * este mod existe. A varredura percebe que a pessoa deixou de estar exilada e fecha o registro.
 *
 * <h2>Custo</h2>
 *
 * <p>A varredura roda uma vez por segundo sobre o mapa de exilados — quase sempre vazio, no pior dia
 * do servidor com dezenas de entradas. Nao ha listener por jogador por tick em lugar nenhum deste
 * mod, e a distancia caminhada vem da estatistica que o vanilla ja conta (ver {@link ForgottenDoor}).
 */
public final class LimboManager {
    /** Faixas de aviso de prazo, da mais larga para a mais estreita. */
    private static final long[] WARN_BANDS = {
            6L * 60 * 60 * 1000,
            60L * 60 * 1000,
            15L * 60 * 1000,
            5L * 60 * 1000,
            60L * 1000
    };

    @Nullable
    private static LimboNarrator narrator;

    private LimboManager() {
    }

    public static LimboNarrator narrator() {
        LimboNarrator current = narrator;
        if (current == null) {
            current = LimboNarrator.create();
            narrator = current;
        }
        return current;
    }

    /** A dimensao do Limbo e a de exilio do {@code aurorion_vidas}: uma so, e configurada la. */
    public static ResourceKey<Level> dimension() {
        return LivesManager.exileDimension();
    }

    // --- Entrada -------------------------------------------------------------------------------

    /**
     * Abre o registro de quem acabou de cair, ou de quem ja estava exilado e ainda nao tinha um.
     *
     * <p>O segundo caso nao e hipotetico: e o que acontece com quem ja estava no Nether de exilio
     * quando este mod entrou no pack, e com quem a staff exilou na mao. Sem isso, essas pessoas
     * ficariam num limbo sem prazo — sem saida e sem registro, que e o pior dos dois mundos.
     */
    public static void openIfNeeded(ServerPlayer player) {
        MinecraftServer server = player.server;
        UUID id = player.getUUID();

        if (!LivesManager.isExiled(server, id)) return;

        LimboData data = LimboData.get(server);
        ExileRecord existing = data.record(id);
        if (existing != null) {
            updateName(data, existing, player);
            return;
        }

        long deadline = LimboConfig.DEADLINE_HOURS.get() * 60L * 60L * 1000L;
        ExileRecord record = new ExileRecord(System.currentTimeMillis(), deadline,
                player.getGameProfile().getName());
        data.open(id, record);

        narrator().fall(player);
        if (LimboConfig.ANNOUNCE_FALL.get()) {
            narrator().announceFall(server, record.lastName(), LimboConfig.ANNOUNCE_NAMES.get());
        }
        AuditLog.record(server, event(AuditEvent.Type.QUEDA, id, record, 0, server, ""));
    }

    // --- Varredura -----------------------------------------------------------------------------

    /**
     * Um passo do relogio do Limbo. Chamada uma vez por segundo pelo listener de tick.
     *
     * <p>O prazo anda pela diferenca entre dois passos <b>reais</b>, e nao pelo relogio de parede:
     * servidor desligado nao produz diferenca, entao uma manutencao de tres dias nao mata ninguem.
     */
    public static void sweep(MinecraftServer server) {
        LimboData data = LimboData.get(server);
        Map<UUID, ExileRecord> active = data.active();
        long elapsed = data.elapsed(System.nanoTime() / 1_000_000L);
        if (active.isEmpty()) {
            return;
        }

        // Ninguem sai do mapa durante a iteracao: o mapa e o objeto vivo do SavedData (ver
        // LimboData#active), e remover dele aqui dentro estoura o proprio laco. As duas formas de
        // sair viram lista e sao aplicadas depois.
        List<UUID> rescued = null;
        List<UUID> crossed = null;

        for (Map.Entry<UUID, ExileRecord> entry : active.entrySet()) {
            UUID id = entry.getKey();
            ExileRecord record = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(id);

            if (!LivesManager.isExiled(server, id)) {
                if (rescued == null) rescued = new ArrayList<>(2);
                rescued.add(id);
                continue;
            }

            if (player != null) {
                updateName(data, record, player);
            }

            // Prazo vencido continua no mapa de proposito: fechar o registro faria o proximo login
            // abrir outro, com prazo cheio, e a pessoa cairia num ciclo sem saida. Ela fica listada
            // como vencida ate a staff decidir — que e justamente a decisao que ainda nao foi tomada.
            data.drain(record, elapsed);
            if (record.reportExpiration()) {
                expire(server, id, record, player);
            }
            // Antes dos desvios abaixo de proposito: quem venceu o prazo, quem saiu da dimensao e
            // quem acabou de voltar tambem precisam de painel certo — e sao justamente os casos que
            // um sync colocado so no fim do laco nunca alcancaria.
            syncIfChanged(player, record, server);

            if (record.remainingMillis() <= 0L) continue;

            if (player == null || !player.isAlive()) continue;
            if (player.level().dimension() != dimension()) continue;

            warnIfBandChanged(player, record);
            if (tickDoor(server, player, record, id)) {
                if (crossed == null) crossed = new ArrayList<>(2);
                crossed.add(id);
            }
            // De novo, porque armar a Porta e revela-la mudam a etapa dentro desta mesma volta. Sem
            // isto, o momento mais dramatico do Limbo chegaria na tela um segundo atrasado.
            syncIfChanged(player, record, server);
        }

        if (rescued != null) {
            for (UUID id : rescued) {
                rescued(server, id, data);
            }
        }
        if (crossed != null) {
            for (UUID id : crossed) {
                ExileRecord record = data.record(id);
                ServerPlayer player = server.getPlayerList().getPlayer(id);
                if (record != null && player != null && record.doorPos() != null
                        && player.level().dimension() == dimension()) {
                    cross(server, player, record, id);
                }
            }
        }
    }

    private static void updateName(LimboData data, ExileRecord record, ServerPlayer player) {
        String name = player.getGameProfile().getName();
        if (!name.equals(record.lastName())) {
            record.setLastName(name);
            data.setDirty();
        }
    }

    /**
     * Manda o painel so quando ele mudaria de cara, mais um reenvio ralo contra deriva.
     *
     * <p>O relogio nao viaja por segundo: o cliente conta sozinho entre dois snapshots (ver
     * {@code ClientLimbo}). Se isto virasse um pacote por segundo por exilado, seria o unico lugar
     * do mod com trafego proporcional a tempo em vez de a evento — exatamente o que a §7.3 proibe.
     */
    private static void syncIfChanged(@Nullable ServerPlayer player, ExileRecord record, MinecraftServer server) {
        if (player == null) return;

        int stage = LimboNetwork.stageOf(record);
        long tick = server.getTickCount();
        if (!record.syncDue(stage, tick)) return;

        LimboNetwork.sync(player);
        record.markSynced(stage, tick);
    }

    private static void warnIfBandChanged(ServerPlayer player, ExileRecord record) {
        long remaining = record.remainingMillis();

        // O cursor so anda para frente (SDD §8.5): um servidor que travou volta pulando os avisos
        // vencidos, em vez de despejar cinco de uma vez na tela de quem ja esta em panico.
        if (record.advanceWarning(WARN_BANDS)) {
            LimboData.get(player.server).setDirty();
            narrator().deadlineBand(player, remaining);
        }
    }

    // --- A Porta do Esquecido ------------------------------------------------------------------

    private static boolean tickDoor(MinecraftServer server, ServerPlayer player, ExileRecord record, UUID id) {
        ServerLevel level = (ServerLevel) player.level();
        long window = LimboConfig.DOOR_WINDOW_HOURS.get() * 60L * 60L * 1000L;
        if (record.remainingMillis() > window || record.rescueAttempted()) {
            if (record.doorArmed() || record.doorPos() != null) {
                ForgottenDoor.erase(server, record);
                record.disarm();
                LimboData.get(server).setDirty();
            }
            leash(player, level);
            return false;
        }

        if (!record.doorArmed()) {
            int target = ForgottenDoor.arm(level, record, player);
            LimboData.get(server).setDirty();
            narrator().doorWindowOpen(player);
            AuditLog.record(server, event(AuditEvent.Type.PORTA_ARMADA, id, record, 0, server,
                    "alvo=" + target + " blocos"));
            return false;
        }

        if (!record.canTryDoor(server.getTickCount())) return false;

        BlockPos door = record.doorPos();
        if (door == null) {
            int walked = record.walkedSince(ForgottenDoor.walkedCm(player));
            if (walked < record.doorTarget()) return false;

            BlockPos placed = ForgottenDoor.reveal(level, player, record);
            // Sem lugar agora: a proxima varredura tenta de novo, alguns passos adiante. Nada se
            // perde — o alvo ja foi batido e continua batido.
            if (placed == null) {
                record.delayDoor(server.getTickCount());
                return false;
            }

            LimboData.get(server).setDirty();
            narrator().doorAppeared(player, placed);
            AuditLog.record(server, event(AuditEvent.Type.PORTA_APARECEU, id, record, walked, server, ""));
            return false;
        }

        return ForgottenDoor.isInside(player, door);
    }

    /**
     * A coleira: antes da janela final, o exilado nao se afasta do ponto de chegada.
     *
     * <p>Serve ao resgate — uma busca de 15 minutos precisa de uma area, nao de um mundo infinito. E
     * cai sozinha quando a Porta arma, que e o momento em que o Limbo deixa de ser sala de espera e
     * vira um lugar para vagar procurando saida.
     */
    private static void leash(ServerPlayer player, ServerLevel level) {
        int radius = LimboConfig.LEASH_RADIUS.get();
        if (radius <= 0) return;

        BlockPos center = ExileSpot.resolve(level, LivesData.get(level.getServer()));
        double dx = player.getX() - (center.getX() + 0.5D);
        double dz = player.getZ() - (center.getZ() + 0.5D);
        double distanceSqr = dx * dx + dz * dz;
        if (distanceSqr <= (double) radius * radius) return;

        double distance = Math.sqrt(distanceSqr);
        double pullback = Math.max(0.0D, radius - 4.0D);
        int x = (int) Math.round(center.getX() + dx / distance * pullback);
        int z = (int) Math.round(center.getZ() + dz / distance * pullback);

        BlockPos ground = SafeSpot.scanDown(level, x, z, level.getMaxBuildHeight() - 2);
        BlockPos target = ground != null ? ground : center;

        player.teleportTo(target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D);
        narrator().leash(player);
    }

    /** Confirma a viagem antes de fechar o exilio; cancelamento devolve a vida ao estado anterior. */
    private static void cross(MinecraftServer server, ServerPlayer player, ExileRecord record,
                              UUID id) {
        if (!LivesManager.isExiled(server, id) || record.rescueAttempted() || !player.isAlive()) return;
        record.delayDoor(server.getTickCount());
        int before = LivesManager.livesOf(server, id);
        boolean returned = false;
        LivesManager.setLives(server, id, 1);
        try {
            returned = returnToOverworld(player);
        } finally {
            if (!returned) LivesManager.setLives(server, id, before);
        }
        if (!returned) return;

        LimboData data = LimboData.get(server);
        int walked = record.walkedSince(ForgottenDoor.walkedCm(player));
        ForgottenDoor.erase(server, record);
        data.close(id);
        int total = data.addForgottenExit(id, record.lastName());

        narrator().escaped(player, total);
        AuditLog.record(server, new AuditEvent(AuditEvent.Type.PORTA_ATRAVESSADA, id, record.lastName(),
                LivesManager.livesOf(server, id), 0L, walked, total,
                "prazo restante era " + record.remainingMillis() / 1000L + "s"));
    }

    // --- Saidas --------------------------------------------------------------------------------

    private static void rescued(MinecraftServer server, UUID id, LimboData data) {
        ExileRecord record = data.record(id);
        if (record == null) return;

        ServerPlayer player = server.getPlayerList().getPlayer(id);
        // Offline: fica pendente ate o login; nao carrega a dimensao para procurar uma entidade.
        if (player == null || !player.isAlive() || !record.canTryDoor(server.getTickCount())) return;
        record.delayDoor(server.getTickCount());
        if (player.level().dimension() == dimension() && !returnToOverworld(player)) return;
        int target = LimboConfig.LIVES_ON_RESCUE.get();
        if (LivesManager.livesOf(server, id) < target) {
            LivesManager.setLives(server, id, target);
        }
        ForgottenDoor.erase(server, record);
        data.close(id);
        narrator().rescued(player);

        AuditLog.record(server, event(AuditEvent.Type.RESGATE, id, record,
                0, server, record.rescueAttempted() ? "" : "sem tentativa registrada antes"));
    }

    private static void expire(MinecraftServer server, UUID id, ExileRecord record, @Nullable ServerPlayer player) {
        ForgottenDoor.erase(server, record);
        record.disarm();
        if (player != null) {
            narrator().expired(player);
        }
        LimboData.get(server).setDirty();
        AuditLog.record(server, event(AuditEvent.Type.PRAZO_VENCIDO, id, record, 0, server, ""));
    }

    /** Alguem tentou buscar esta pessoa — desliga a Porta do Esquecido para ela. */
    public static boolean markRescueAttempt(MinecraftServer server, UUID target) {
        ExileRecord record = LimboData.get(server).record(target);
        if (record == null || record.rescueAttempted()) return false;

        ForgottenDoor.erase(server, record);
        record.markRescueAttempted();
        LimboData.get(server).setDirty();
        AuditLog.record(server, event(AuditEvent.Type.TENTATIVA_RESGATE, target, record, 0, server, ""));
        return true;
    }

    public static boolean setDeadline(MinecraftServer server, UUID id, long millis) {
        LimboData data = LimboData.get(server);
        ExileRecord record = data.record(id);
        if (record == null) return false;
        long window = LimboConfig.DOOR_WINDOW_HOURS.get() * 3_600_000L;
        if (millis <= 0 || millis > window) {
            ForgottenDoor.erase(server, record);
            record.disarm();
        }
        record.setRemaining(millis);
        data.setDirty();
        AuditLog.record(server, event(AuditEvent.Type.PRAZO_AJUSTADO, id, record, 0, server, ""));
        return true;
    }

    private static boolean returnToOverworld(ServerPlayer player) {
        ServerLevel overworld = player.server.overworld();
        BlockPos landing = SafeSpot.aroundColumn(overworld, overworld.getSharedSpawnPos(), 8,
                overworld.getMaxBuildHeight() - 2);
        if (landing == null) return false;
        ForgottenDoor.authorize(player, overworld.dimension());
        try {
            var moved = player.changeDimension(new DimensionTransition(overworld,
                    landing.getBottomCenter(), Vec3.ZERO, player.getYRot(), player.getXRot(),
                    DimensionTransition.DO_NOTHING));
            return moved != null && moved.level() == overworld;
        } finally {
            ForgottenDoor.clear();
        }
    }

    private static AuditEvent event(AuditEvent.Type type, UUID id, ExileRecord record, int walked,
                                    MinecraftServer server, String detail) {
        return new AuditEvent(type, id, record.lastName(), LivesManager.livesOf(server, id),
                record.remainingMillis(), walked, LimboData.get(server).forgottenExits(id), detail);
    }

    /** O narrador guarda estado ligado ao processo, nao ao mundo; zerar no desligamento e barato e correto. */
    public static void reset() {
        narrator = null;
        ForgottenDoor.clear();
    }
}
