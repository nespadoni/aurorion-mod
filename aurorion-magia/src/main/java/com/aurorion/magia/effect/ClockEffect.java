package com.aurorion.magia.effect;

import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.EffectCure;

import java.util.Set;
import java.util.function.Predicate;

/**
 * Efeito-relogio: o unico ponto periodico de uma passiva ou canalizacao, no tick da propria entidade
 * afetada — o mesmo desenho da {@link DreadAuraEffect}. Sem ninguem com o efeito, o custo e zero.
 *
 * <p>O intervalo e conferido no {@code tickCount} da entidade, e nao na duracao: efeito infinito fica
 * com a duracao em {@code -1} para sempre, e {@code duration % intervalo} nunca daria zero.
 *
 * <p>Leite nao tira: quem decide quando o relogio acaba e a mecanica dona dele. Devolver {@code false}
 * do tick faz o vanilla remover o efeito, e a remocao chega ao {@code MobEffectEvent.Remove} como
 * qualquer outra — e la que o estado de fora e solto.
 */
public final class ClockEffect extends MagiaEffect {
    private final int interval;
    private final Predicate<LivingEntity> tick;

    /**
     * @param interval de quantos em quantos ticks {@code tick} roda (1 = todo tick)
     * @param tick     so no servidor; {@code false} acaba o efeito
     */
    public ClockEffect(MobEffectCategory category, int color, int interval, Predicate<LivingEntity> tick) {
        super(category, color);
        this.interval = Math.max(1, interval);
        this.tick = tick;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.level().isClientSide || entity.tickCount % interval != 0) return true;
        return tick.test(entity);
    }

    @Override
    public void fillEffectCures(Set<EffectCure> cures, MobEffectInstance instance) {
    }
}
