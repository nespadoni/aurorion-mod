package com.aurorion.magia.spell;

import com.aurorion.magia.AurorionMagia;
import com.aurorion.magia.registry.MagiaEffects;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/**
 * Quem prende o olhar de quem. O captor fica no {@code persistentData} do alvo, ao lado do efeito.
 */
public final class Gaze {
    private static final String KEY_CAPTOR = AurorionMagia.MOD_ID + ":captor";

    private Gaze() {
    }

    public static void capture(LivingEntity target, LivingEntity captor, int ticks) {
        target.getPersistentData().putUUID(KEY_CAPTOR, captor.getUUID());
        target.addEffect(new MobEffectInstance(MagiaEffects.CAPTIVE, ticks, 0, false, false, true), captor);
        if (target instanceof Mob mob) holdMob(mob);
    }

    /** Tick do efeito em mob: a cabeca volta para o captor, a cada 2 ticks. */
    public static void holdMob(Mob mob) {
        if (!(mob.level() instanceof ServerLevel level) || !mob.getPersistentData().hasUUID(KEY_CAPTOR)) return;
        Entity captor = level.getEntity(mob.getPersistentData().getUUID(KEY_CAPTOR));
        if (captor != null) mob.getLookControl().setLookAt(captor, 90f, 90f);
    }

    public static void release(LivingEntity entity) {
        entity.getPersistentData().remove(KEY_CAPTOR);
    }
}
