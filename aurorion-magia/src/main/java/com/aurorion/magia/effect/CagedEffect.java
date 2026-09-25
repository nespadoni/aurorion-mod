package com.aurorion.magia.effect;

import com.aurorion.magia.AurorionMagia;
import com.aurorion.magia.spell.WaterCage;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Carcer Aquae: preso dentro da bolha, boiando.
 *
 * <p>Nao anda, nao pula e nao cai — a bolha o segura no lugar em que a magia o pegou, um pouco acima
 * do chao. Continua respirando (a magia de afogar e outra) e continua vendo tudo; o que ele nao faz e
 * sair dali sozinho.
 *
 * <p>A bolha e <b>quebravel de fora</b>: qualquer um que bata nela a estoura ({@code WaterCage}). E de
 * proposito que a magia tenha resgate — prender um refem no meio da praça sem ninguem poder tira-lo
 * de la vira impasse, e nao cena.
 */
public final class CagedEffect extends MobEffect {
    public CagedEffect() {
        super(MobEffectCategory.HARMFUL, 0x2E86C1);
        addAttributeModifier(Attributes.MOVEMENT_SPEED, AurorionMagia.id("carcer_speed"),
                -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        addAttributeModifier(Attributes.JUMP_STRENGTH, AurorionMagia.id("carcer_jump"),
                -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true;
    }

    /** Todo tick, e so no preso: manter boiando e cancelar o que o cliente dele tentou andar. */
    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (!entity.level().isClientSide) WaterCage.hold(entity);
        return true;
    }
}
