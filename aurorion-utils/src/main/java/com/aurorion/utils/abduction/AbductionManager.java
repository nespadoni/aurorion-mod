package com.aurorion.utils.abduction;

import com.aurorion.utils.config.AbductionConfig;
import com.aurorion.utils.entity.AbductionBeamEntity;
import com.aurorion.utils.entity.ModEntities;
import com.aurorion.utils.sound.ModSounds;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Ponto unico que sabe orquestrar uma abducao: valida, spawna a {@link AbductionBeamEntity},
 * registra o estado em memoria (nunca persistido — se o servidor cair no meio de uma abducao, a
 * pior consequencia e o jogador ficar montado no feixe onde estava, sem dado corrompido) e avanca
 * cada abducao ativa por tick (via {@link AbductionTicker}) atraves das 3 fases descritas em
 * {@link ActiveAbduction}.
 *
 * <p>A imobilidade do alvo durante HOLD/ASCEND vem de montaria (o alvo "cavalga" a propria
 * {@link AbductionBeamEntity}), nao de reposicionar o jogador a forca a cada tick: e a mesma
 * mecanica nativa do jogo usada por {@code FreezeManager} pro {@code /freeze} — nao anda, nao
 * pula, continua olhando livremente ao redor, e nenhum {@code MobEffect} entra em jogo (entao
 * nada como leite tem qualquer efeito sobre isso).</p>
 */
public final class AbductionManager {
    public enum Result {
        OK,
        ALREADY_ACTIVE,
        NO_SAVED_ORIGIN
    }

    private static final Map<UUID, ActiveAbduction> ACTIVE = new HashMap<>();

    private AbductionManager() {
    }

    public static boolean isActive(UUID playerId) {
        return ACTIVE.containsKey(playerId);
    }

    /**
     * {@code /abduzir <alvo> [destino] [cor]} — destino default e quem executou o comando.
     *
     * @param beamColor RGB escolhido no comando, ou {@code null} pra usar o da config.
     */
    public static Result startAbduction(ServerPlayer target, ServerPlayer destinationSource, @Nullable Integer beamColor) {
        if (isActive(target.getUUID())) return Result.ALREADY_ACTIVE;

        TeleportSpot origin = TeleportSpot.of(target);
        AbductionOriginData.get(target.getServer()).set(target.getUUID(), origin);

        begin(target, origin, TeleportSpot.of(destinationSource), false, beamColor);
        return Result.OK;
    }

    /** {@code /abduzir voltar <alvo> [cor]} — leva de volta pra ultima origem salva. */
    public static Result startReturn(ServerPlayer target, @Nullable Integer beamColor) {
        if (isActive(target.getUUID())) return Result.ALREADY_ACTIVE;

        TeleportSpot savedOrigin = AbductionOriginData.get(target.getServer()).get(target.getUUID());
        if (savedOrigin == null) return Result.NO_SAVED_ORIGIN;

        begin(target, TeleportSpot.of(target), savedOrigin, true, beamColor);
        return Result.OK;
    }

    private static void begin(ServerPlayer target, TeleportSpot startSpot, TeleportSpot destination,
                              boolean returnTrip, @Nullable Integer beamColor) {
        MinecraftServer server = target.getServer();
        ServerLevel level = target.serverLevel();

        int ascentHeight = computeAscentHeight(level, target.position(), AbductionConfig.ASCENT_HEIGHT_BLOCKS.get());
        int holdTicks = AbductionConfig.HOLD_DURATION_TICKS.get();
        int ascentTicks = AbductionConfig.ASCENT_DURATION_TICKS.get();
        int retractTicks = AbductionConfig.RETRACT_DURATION_TICKS.get();
        float radius = AbductionConfig.BEAM_RADIUS.get().floatValue();
        int color = beamColor != null ? beamColor : configColor();

        AbductionBeamEntity beam = new AbductionBeamEntity(ModEntities.ABDUCTION_BEAM.get(), level);
        beam.setPos(target.getX(), target.getY(), target.getZ());
        beam.setTargetPlayer(target.getUUID());
        beam.setRadius(radius);
        beam.setColor(color);
        beam.setGroundY((float) target.getY());
        beam.setPhase(AbductionBeamEntity.PHASE_HOLD);
        level.addFreshEntity(beam);

        target.startRiding(beam, true);
        target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, holdTicks + ascentTicks + 5, 0, false, false));

        level.playSound(null, target.getX(), target.getY(), target.getZ(),
                ModSounds.ABDUCTION_BEAM.get(), SoundSource.PLAYERS, 1.0F, 1.0F);

        ActiveAbduction abduction = new ActiveAbduction(target.getUUID(), beam, startSpot, destination,
                returnTrip, holdTicks, ascentTicks, retractTicks, ascentHeight, server.getTickCount());
        ACTIVE.put(target.getUUID(), abduction);
    }

    /** Raycast pra cima a partir do jogador, pra subida nunca enfiar ninguem dentro de um teto. */
    private static int computeAscentHeight(ServerLevel level, Vec3 from, int configuredHeight) {
        Vec3 to = from.add(0.0, configuredHeight + 1.0, 0.0);
        var hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                CollisionContext.empty()));

        if (hit.getType() != HitResult.Type.BLOCK) return configuredHeight;

        int clearance = (int) Math.floor(hit.getLocation().y - from.y) - 1;
        return Math.max(1, Math.min(configuredHeight, clearance));
    }

    /** Aceita os mesmos valores do argumento {@code <cor>} do comando — nome da paleta ou hex. */
    private static int configColor() {
        Integer parsed = BeamColor.parse(AbductionConfig.BEAM_COLOR_RGB.get());
        return parsed != null ? parsed : BeamColor.ROXO.rgb();
    }

    /** Chamado uma vez por tick do servidor por {@link AbductionTicker}. */
    static void tickAll(MinecraftServer server) {
        if (ACTIVE.isEmpty()) return;

        List<ActiveAbduction> snapshot = new ArrayList<>(ACTIVE.values());
        for (ActiveAbduction abduction : snapshot) {
            switch (abduction.phase) {
                case HOLD -> tickHold(server, abduction);
                case ASCEND -> tickAscend(server, abduction);
                case RETRACT -> tickRetract(server, abduction);
            }
        }
    }

    private static void tickHold(MinecraftServer server, ActiveAbduction abduction) {
        ServerPlayer target = server.getPlayerList().getPlayer(abduction.targetId);
        if (target == null) {
            abort(abduction);
            return;
        }

        if (server.getTickCount() - abduction.phaseStartTick >= abduction.holdTicks) {
            abduction.phase = ActiveAbduction.Phase.ASCEND;
            abduction.phaseStartTick = server.getTickCount();
            abduction.beam.setPhase(AbductionBeamEntity.PHASE_ASCEND);
        }
    }

    private static void tickAscend(MinecraftServer server, ActiveAbduction abduction) {
        ServerPlayer target = server.getPlayerList().getPlayer(abduction.targetId);
        if (target == null) {
            abort(abduction);
            return;
        }

        int elapsed = server.getTickCount() - abduction.phaseStartTick;
        float progress = Mth.clamp((float) elapsed / abduction.ascentTicks, 0.0F, 1.0F);

        double newY = abduction.startSpot.y() + abduction.ascentHeightBlocks * progress;
        abduction.beam.setPos(abduction.startSpot.x(), newY, abduction.startSpot.z());

        if (progress >= 1.0F) {
            completeTeleport(server, target, abduction);
        }
    }

    private static void tickRetract(MinecraftServer server, ActiveAbduction abduction) {
        int elapsed = server.getTickCount() - abduction.phaseStartTick;
        float progress = Mth.clamp((float) elapsed / abduction.retractTicks, 0.0F, 1.0F);
        abduction.beam.setProgress(progress);

        if (progress >= 1.0F) {
            abduction.beam.discard();
            ACTIVE.remove(abduction.targetId);
        }
    }

    private static void completeTeleport(MinecraftServer server, ServerPlayer target, ActiveAbduction abduction) {
        target.stopRiding();

        TeleportSpot destination = abduction.destination;
        ServerLevel destLevel = server.getLevel(destination.dimension());
        if (destLevel != null) {
            target.teleportTo(destLevel, destination.x(), destination.y(), destination.z(),
                    Set.of(), destination.yaw(), destination.pitch());
        }
        target.setDeltaMovement(Vec3.ZERO);

        if (abduction.returnTrip) {
            AbductionOriginData.get(server).clear(abduction.targetId);
        }

        abduction.phase = ActiveAbduction.Phase.RETRACT;
        abduction.phaseStartTick = server.getTickCount();
        abduction.beam.setPhase(AbductionBeamEntity.PHASE_RETRACT);
    }

    /** Alvo desconectou no meio de HOLD/ASCEND — nao ha pra onde puxar, so limpa o feixe. */
    private static void abort(ActiveAbduction abduction) {
        abduction.beam.discard();
        ACTIVE.remove(abduction.targetId);
    }
}
