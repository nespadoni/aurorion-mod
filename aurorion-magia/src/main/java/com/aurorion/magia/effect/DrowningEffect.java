package com.aurorion.magia.effect;

import com.aurorion.magia.AurorionMagia;
import com.aurorion.magia.spell.Drowning;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Submersio: o pulmao afoga em terra firme. A regra esta em {@link Drowning}; aqui fica so o relogio.
 *
 * <p>O tick roda a cada {@value #INTERVAL} ticks e so em quem esta afogando: uma subtracao no ar do
 * vanilla e, depois que ele zera, um dano de meio em meio segundo.
 */
public final class DrowningEffect extends MobEffect {
    private static final int INTERVAL = 10;
    /** Quanto de ar (em ticks do vanilla) o pulmao perde por pulso: o ar cheio dura ~7,5 s. */
    private static final int AIR_PER_PULSE = 40;
    private static final float DAMAGE = 2;

    public DrowningEffect() {
        super(MobEffectCategory.HARMFUL, 0x1E5F8A);
        // O corpo pesa: afogar nao e so perder ar, e nao conseguir correr atras de socorro.
        addAttributeModifier(Attributes.MOVEMENT_SPEED, AurorionMagia.id("submersio_speed"),
                -0.35, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % INTERVAL == 0;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.level().isClientSide) return true;
        int air = entity.getAirSupply();
        if (air > -20) {
            entity.setAirSupply(air - AIR_PER_PULSE);
            return true;
        }
        entity.setAirSupply(-20);
        Drowning.suffocate(entity, DAMAGE);
        return true;
    }
}
