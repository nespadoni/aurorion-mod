package com.aurorion.magia.effect;

import com.aurorion.magia.spell.Telekinesis;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * Arremessado pela Mao do Algoz. Dura no maximo um segundo e meio depois de solto, e e o unico
 * efeito que olha a entidade todo tick: precisa pegar o instante em que ela bate na parede. Some na
 * primeira colisao ou quando o voo perde forca.
 */
public final class ThrownEffect extends MobEffect {
    public ThrownEffect() {
        super(MobEffectCategory.HARMFUL, 0xB9A7FF);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.level().isClientSide) return true;
        return !Telekinesis.checkImpact(entity, amplifier);
    }
}
