package com.aurorion.magia.effect;

import com.aurorion.magia.AurorionMagia;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Paralisia por dor. Sem tick nenhum: a paralisia e so modificador de atributo, que o vanilla aplica
 * ao entrar e tira ao sair — e sincroniza com o cliente do jogador, que e quem de fato move o corpo.
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
}
