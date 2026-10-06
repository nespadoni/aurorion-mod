package com.aurorion.magia.effect;

import com.aurorion.magia.spell.Possession;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.EffectCure;

import java.util.Set;

/**
 * Quem esta dentro de outro corpo ({@link Possession}). E o relogio da possessao: tica todo tick, e
 * so em quem possui alguem — com ninguem possuido no servidor, o custo e zero.
 */
public final class PossessingEffect extends MobEffect {
    public PossessingEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x2A0B3D);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true;
    }

    /** Devolver {@code false} faz o vanilla remover o efeito, e a remocao devolve os dois corpos. */
    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.level().isClientSide) return true;
        return Possession.tick(entity);
    }

    /** Leite nao expulsa ninguem de um corpo: so a segunda conjuracao, o tempo ou a morte. */
    @Override
    public void fillEffectCures(Set<EffectCure> cures, MobEffectInstance instance) {
    }
}
