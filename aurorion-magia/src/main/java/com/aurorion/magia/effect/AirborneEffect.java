package com.aurorion.magia.effect;

import com.aurorion.magia.registry.MagiaEffects;
import com.aurorion.magia.spell.Impetus;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/**
 * Impeto do Vento: lancado para o alto.
 *
 * <p>Enquanto dura, a queda acumulada e zerada a cada tick — foi o vento que ergueu a pessoa, e descer
 * de seis blocos nao pode matar ninguem, nem no nivel 1, que por regra nao fere. Quem esta no ar nao
 * conjura (portao em {@code MagiaServerEvents}). O efeito sai sozinho ao tocar o chao, ou no teto de
 * {@value Impetus#AIRBORNE_TICKS} ticks.
 */
public final class AirborneEffect extends MobEffect {
    /** Carencia antes de conferir o chao: no primeiro tick o lancado ainda esta pisando nele. */
    private static final int GRACE_TICKS = 5;

    public AirborneEffect() {
        super(MobEffectCategory.HARMFUL, 0xBFEFFF);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.level().isClientSide) return true;
        entity.resetFallDistance();
        MobEffectInstance instance = entity.getEffect(MagiaEffects.AIRBORNE);
        boolean settled = instance != null && instance.getDuration() < Impetus.AIRBORNE_TICKS - GRACE_TICKS
                && (entity.onGround() || entity.isInWater());
        return !settled;
    }
}
