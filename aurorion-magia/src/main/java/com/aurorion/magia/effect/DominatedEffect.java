package com.aurorion.magia.effect;

import com.aurorion.magia.spell.Domination;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/**
 * Mob dominado. O tick do efeito e o unico ponto periodico do Imperium: roda so em quem esta
 * dominado, uma vez por segundo, dentro do tick que o vanilla ja faz da entidade — nao ha varredura
 * global no {@code ServerTickEvent}.
 */
public final class DominatedEffect extends MobEffect {
    private static final int INTERVAL = 20;

    public DominatedEffect() {
        super(MobEffectCategory.NEUTRAL, 0xC8F0DC);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % INTERVAL == 0;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (!entity.level().isClientSide && entity instanceof Mob mob) {
            Domination.maintain(mob);
        }
        return true;
    }
}
