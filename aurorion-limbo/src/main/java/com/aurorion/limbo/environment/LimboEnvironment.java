package com.aurorion.limbo.environment;

import com.aurorion.limbo.exile.LimboManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Regras ambientais que precisam acompanhar o jogador, sem tocar no terreno depois da geração. */
public final class LimboEnvironment {
    private static final String DARKNESS_MARKER = "aurorion_limbo:environment_darkness";
    private static final int FIRST_SOUND_MIN_TICKS = 8 * 20;
    private static final int FIRST_SOUND_MAX_TICKS = 18 * 20;
    private static final int SOUND_MIN_TICKS = 18 * 20;
    private static final int SOUND_MAX_TICKS = 42 * 20;

    /** Um prazo por pessoa presente; não cresce com chunks nem com a duração do mundo. */
    private static final Map<UUID, Long> NEXT_SOUND = new HashMap<>();

    private LimboEnvironment() {
    }

    /** Reconciliação de um segundo: cobre reloads, comandos e mods que mexem no efeito diretamente. */
    public static void tick(MinecraftServer server) {
        long now = server.getTickCount();
        ServerLevel level = server.getLevel(LimboManager.dimension());
        if (level == null) return;

        // A lista pertence somente ao Limbo. Com a dimensão vazia, os 80 jogadores do servidor
        // não entram neste laço.
        for (ServerPlayer player : level.players()) {
            enforceDarkness(player);
            tickSound(player, now);
        }
    }

    /** Entrada e respawn não esperam a próxima varredura para escurecer a tela. */
    public static void reconcile(ServerPlayer player) {
        if (isInside(player)) {
            enforceDarkness(player);
            scheduleFirstSound(player, player.server.getTickCount());
        } else {
            leave(player);
        }
    }

    /** Leite, /effect clear e curas de outros mods passam pelo evento Remove. */
    public static void keepDarkness(MobEffectEvent.Remove event) {
        if (event.getEntity() instanceof ServerPlayer player
                && isInside(player)
                && event.getEffect().equals(MobEffects.DARKNESS)) {
            event.setCanceled(true);
        }
    }

    /** Protege também o fim natural de uma instância mais forte aplicada por outro mod. */
    public static void keepDarkness(MobEffectEvent.Expired event) {
        if (event.getEntity() instanceof ServerPlayer player
                && isInside(player)
                && event.getEffectInstance().getEffect().equals(MobEffects.DARKNESS)) {
            event.setCanceled(true);
        }
    }

    private static boolean isInside(ServerPlayer player) {
        return player.level().dimension() == LimboManager.dimension();
    }

    private static void enforceDarkness(ServerPlayer player) {
        player.getPersistentData().putBoolean(DARKNESS_MARKER, true);
        if (!player.hasEffect(MobEffects.DARKNESS)) {
            player.addEffect(new MobEffectInstance(MobEffects.DARKNESS,
                    MobEffectInstance.INFINITE_DURATION, 0, true, false, true));
        }
    }

    private static void leave(ServerPlayer player) {
        NEXT_SOUND.remove(player.getUUID());
        if (!player.getPersistentData().getBoolean(DARKNESS_MARKER)) return;

        player.getPersistentData().remove(DARKNESS_MARKER);
        player.removeEffect(MobEffects.DARKNESS);
    }

    /** Desconectar preserva o efeito no NBT, mas o agendamento sonoro é apenas desta sessão. */
    public static void disconnect(ServerPlayer player) {
        NEXT_SOUND.remove(player.getUUID());
    }

    private static void tickSound(ServerPlayer player, long now) {
        Long next = NEXT_SOUND.get(player.getUUID());
        if (next == null) {
            scheduleFirstSound(player, now);
            return;
        }
        if (now < next) return;

        ServerLevel level = player.serverLevel();
        double angle = player.getRandom().nextDouble() * Mth.TWO_PI;
        double distance = Mth.nextDouble(player.getRandom(), 7.0D, 16.0D);
        double x = player.getX() + Math.cos(angle) * distance;
        double y = player.getY() + Mth.nextInt(player.getRandom(), -3, 3);
        double z = player.getZ() + Math.sin(angle) * distance;
        float volume = Mth.nextFloat(player.getRandom(), 0.55F, 0.85F);
        float pitch = Mth.nextFloat(player.getRandom(), 0.78F, 1.02F);

        level.playSound(null, x, y, z, randomSound(player), SoundSource.HOSTILE, volume, pitch);
        NEXT_SOUND.put(player.getUUID(), now
                + Mth.nextInt(player.getRandom(), SOUND_MIN_TICKS, SOUND_MAX_TICKS));
    }

    private static void scheduleFirstSound(ServerPlayer player, long now) {
        NEXT_SOUND.computeIfAbsent(player.getUUID(), ignored -> now
                + Mth.nextInt(player.getRandom(), FIRST_SOUND_MIN_TICKS, FIRST_SOUND_MAX_TICKS));
    }

    private static SoundEvent randomSound(ServerPlayer player) {
        return switch (player.getRandom().nextInt(10)) {
            case 0, 1, 2 -> SoundEvents.ZOMBIE_AMBIENT;
            case 3, 4 -> SoundEvents.SKELETON_AMBIENT;
            case 5, 6 -> SoundEvents.SPIDER_AMBIENT;
            case 7 -> SoundEvents.ENDERMAN_AMBIENT;
            case 8 -> SoundEvents.WITHER_SKELETON_AMBIENT;
            default -> SoundEvents.WARDEN_AMBIENT;
        };
    }

    public static void reset() {
        NEXT_SOUND.clear();
    }
}
