package com.aurorion.magia.effect;

import com.aurorion.magia.spell.ProcellaCorvorumSpell;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * Tempestade de Corvos: a revoada em volta de quem conjurou. E o relogio da aura — um pulso a cada
 * {@value ProcellaCorvorumSpell#PULSE_TICKS} ticks, so em quem a carrega, por no maximo cinco segundos.
 */
public final class CrowStormEffect extends MobEffect {
    public CrowStormEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x1A1420);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % ProcellaCorvorumSpell.PULSE_TICKS == 0;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (!entity.level().isClientSide) ProcellaCorvorumSpell.pulse(entity, amplifier + 1);
        return true;
    }
}
