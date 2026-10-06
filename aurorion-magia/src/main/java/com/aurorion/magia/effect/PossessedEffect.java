package com.aurorion.magia.effect;

import com.aurorion.magia.AurorionMagia;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.common.EffectCure;

import java.util.Set;

/**
 * O corpo tomado pela Possessao. Nao anda nem pula por conta propria (quem move o corpo e o
 * possessor, pelo servidor), nao usa item, nao ataca, nao conjura. Fala — o chat continua dele.
 */
public final class PossessedEffect extends MobEffect {
    public PossessedEffect() {
        super(MobEffectCategory.HARMFUL, 0x22002F);
        addAttributeModifier(Attributes.MOVEMENT_SPEED, AurorionMagia.id("possuido_speed"),
                -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        addAttributeModifier(Attributes.JUMP_STRENGTH, AurorionMagia.id("possuido_jump"),
                -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    @Override
    public void fillEffectCures(Set<EffectCure> cures, MobEffectInstance instance) {
    }
}
