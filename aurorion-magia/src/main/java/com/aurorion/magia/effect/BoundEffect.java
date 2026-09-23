package com.aurorion.magia.effect;

import com.aurorion.magia.spell.Binding;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * Vinculum Carnificis: a corrente invisivel ate a ancora.
 *
 * <p>A conferencia de distancia roda aqui, no tick que o vanilla ja faz da entidade presa, 5 vezes
 * por segundo — e so em quem esta preso. Nao ha lista global de presos varrida por tick.
 */
public final class BoundEffect extends MobEffect {
    public static final int INTERVAL = 4;

    public BoundEffect() {
        super(MobEffectCategory.HARMFUL, 0x8C1020);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % INTERVAL == 0;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (!entity.level().isClientSide) Binding.enforce(entity);
        return true;
    }
}
