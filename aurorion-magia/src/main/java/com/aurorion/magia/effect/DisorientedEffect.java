package com.aurorion.magia.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * Desorientacao do Imperium Mentis em jogador. O efeito em si nao faz nada por tick; ele e o estado
 * que os outros lados consultam:
 *
 * <ul>
 *   <li>servidor: {@code MagiaServerEvents} cancela conjuracao e uso de item enquanto ativo;</li>
 *   <li>cliente do afetado: inverte o movimento e escurece a tela.</li>
 * </ul>
 */
public final class DisorientedEffect extends MobEffect {
    public DisorientedEffect() {
        super(MobEffectCategory.HARMFUL, 0x9FE3C8);
    }
}
