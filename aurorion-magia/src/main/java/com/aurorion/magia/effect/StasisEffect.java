package com.aurorion.magia.effect;

import com.aurorion.magia.AurorionMagia;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Tempera do Destino: preso numa redoma dourada, fora do tempo. Nao anda, nao pula, nao cai — a
 * gravidade tambem vai a zero, entao quem estava no ar fica suspenso ali. O dano nao entra
 * ({@code MagiaServerEvents.onIncomingDamage}) e as maos nao respondem.
 *
 * <p>Os ids dos modificadores nao sao os da antiga "estase" do Sequestro de Impulso: aqueles o
 * {@code EffectCleanup} solta sempre, porque a magia saiu do mod.
 */
public final class StasisEffect extends MobEffect {
    public StasisEffect() {
        super(MobEffectCategory.NEUTRAL, 0xF2C94C);
        addAttributeModifier(Attributes.MOVEMENT_SPEED, AurorionMagia.id("estase_fati_speed"),
                -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        addAttributeModifier(Attributes.JUMP_STRENGTH, AurorionMagia.id("estase_fati_jump"),
                -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        addAttributeModifier(Attributes.GRAVITY, AurorionMagia.id("estase_fati_gravity"),
                -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }
}
