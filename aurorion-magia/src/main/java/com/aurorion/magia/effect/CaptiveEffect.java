package com.aurorion.magia.effect;

import com.aurorion.magia.AurorionMagia;
import com.aurorion.magia.spell.Gaze;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Aspectus Captus: o olhar preso em quem conjurou. Anda devagar, fala, mas nao consegue desviar o
 * rosto.
 *
 * <p>Em jogador, quem vira a camera e o cliente dele (ver {@code MagiaClientEvents}): e o cliente que
 * decide para onde o jogador olha, e a rotacao volta ao servidor pelo movimento normal — os outros
 * veem a cabeca virar. Em mob, o tick do efeito aponta a {@code LookControl} para o conjurador.
 */
public final class CaptiveEffect extends MobEffect {
    public CaptiveEffect() {
        super(MobEffectCategory.HARMFUL, 0x6B2F8F);
        addAttributeModifier(Attributes.MOVEMENT_SPEED, AurorionMagia.id("cativo_speed"),
                -0.6, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % 2 == 0;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (!entity.level().isClientSide && entity instanceof Mob mob) Gaze.holdMob(mob);
        return true;
    }
}
