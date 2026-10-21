package com.aurorion.magia.effect;

import com.aurorion.magia.AurorionMagia;
import com.aurorion.magia.spell.PositionHold;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Paralisia por dor. A paralisia e modificador de atributo, que o vanilla aplica ao entrar e tira ao
 * sair — e sincroniza com o cliente do jogador, que e quem de fato move o corpo. Como cliente
 * modificado ignora atributo, o tick confere a posicao no servidor ({@link PositionHold}).
 *
 * <p>{@code ADD_MULTIPLIED_TOTAL -1} zera o valor final mesmo com Velocidade ou botas de outro mod
 * somando antes. O tremor de camera e desenhado pelo cliente ao ver este efeito ativo.
 */
public final class CruciatusEffect extends MobEffect {
    public CruciatusEffect() {
        super(MobEffectCategory.HARMFUL, 0x7A0010);
        addAttributeModifier(Attributes.MOVEMENT_SPEED, AurorionMagia.id("cruciatus_speed"),
                -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        addAttributeModifier(Attributes.JUMP_STRENGTH, AurorionMagia.id("cruciatus_jump"),
                -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true;
    }

    /**
     * Jogador com cliente modificado ignora o atributo. O servidor segura o corpo no plano (x/z, folga
     * de um bloco); a altura fica livre porque o Tormento Coletivo e quem ergue a vitima. Ver
     * {@link PositionHold}.
     */
    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (!entity.level().isClientSide) PositionHold.hold(entity, PositionHold.CRUCIATUS, true, 1.0);
        return true;
    }
}
