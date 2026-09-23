package com.aurorion.magia.effect;

import com.aurorion.magia.AurorionMagia;
import com.aurorion.magia.compat.EmotecraftCompat;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Genua Flecte: de joelhos. Movimento quase zero e sem pulo; a voz fica livre de proposito, e a
 * mao tambem — ajoelhado nao e paralisado.
 *
 * <p>O tick, uma vez por segundo e so em jogador ajoelhado, confere se o emote de joelhos ainda
 * esta tocando (o jogador pode ter trocado de dimensao ou relogado) e o reinicia se nao estiver.
 */
public final class KneelingEffect extends MobEffect {
    private static final int INTERVAL = 20;

    public KneelingEffect() {
        super(MobEffectCategory.HARMFUL, 0xB7A4D6);
        addAttributeModifier(Attributes.MOVEMENT_SPEED, AurorionMagia.id("genua_speed"),
                -0.95, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        addAttributeModifier(Attributes.JUMP_STRENGTH, AurorionMagia.id("genua_jump"),
                -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % INTERVAL == 0;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity instanceof ServerPlayer player) EmotecraftCompat.ensureKneeling(player);
        return true;
    }
}
