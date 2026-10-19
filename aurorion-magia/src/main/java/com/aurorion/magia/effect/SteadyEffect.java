package com.aurorion.magia.effect;

import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.neoforge.common.EffectCure;

import java.util.Set;

/**
 * Estado que leite e totem nao desfazem: o cansaco de quem voltou ao proprio corpo, a bencao da lua.
 * So o tempo (ou a mecanica que o pos) o tira.
 */
public class SteadyEffect extends MagiaEffect {
    public SteadyEffect(MobEffectCategory category, int color) {
        super(category, color);
    }

    @Override
    public void fillEffectCures(Set<EffectCure> cures, MobEffectInstance instance) {
    }
}
