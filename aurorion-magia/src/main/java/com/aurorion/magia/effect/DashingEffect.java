package com.aurorion.magia.effect;

import com.aurorion.magia.spell.Impetus;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * Impeto do Vento: avancando. Tica todo tick, e so em quem esta no meio da investida, que dura no
 * maximo {@value Impetus#MAX_TICKS} ticks. Devolver {@code false} (a investida acabou) faz o vanilla
 * remover o efeito.
 */
public final class DashingEffect extends MobEffect {
    public DashingEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xDFFFF4);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.level().isClientSide) return true;
        return Impetus.tick(entity);
    }
}
