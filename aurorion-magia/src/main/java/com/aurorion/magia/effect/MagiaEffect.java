package com.aurorion.magia.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * Efeito sem comportamento proprio: so icone, cor e, quando houver, modificadores de atributo. E o
 * estado que os eventos do servidor e o cliente consultam — silenciado, estase, ferro vinculado.
 */
public class MagiaEffect extends MobEffect {
    public MagiaEffect(MobEffectCategory category, int color) {
        super(category, color);
    }
}
