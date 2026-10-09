package com.aurorion.magia.effect;

import com.aurorion.magia.spell.SoulRealm;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.EffectCure;
import java.util.Set;

/** O prazo do duelo e salvo pelo vanilla; as ancoras ficam no SavedData do mundo. */
public final class SoulRealmEffect extends MobEffect {
    public SoulRealmEffect() { super(MobEffectCategory.NEUTRAL, 0x37E6C2); }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) { return true; }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        return entity.level().isClientSide || SoulRealm.tick(entity);
    }

    @Override
    public void fillEffectCures(Set<EffectCure> cures, MobEffectInstance instance) { }
}
