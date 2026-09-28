package com.aurorion.magia.effect;

import com.aurorion.magia.spell.TimeStop;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.EffectCure;

import java.util.Set;

/**
 * O marcador de quem esta com o tempo parado — e o relogio da zona ({@link TimeStop}).
 *
 * <p>Mesmo desenho do {@code DreadAuraEffect}: infinito e invisivel, tica so no conjurador, e sem
 * zona ligada no servidor o custo e zero. O intervalo e conferido no tick, e nao em
 * {@link #shouldApplyEffectTickThisTick}, porque a duracao de um efeito infinito nao anda.
 */
public final class TimeStopEffect extends MobEffect {
    public TimeStopEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x0A0612);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true;
    }

    /** Devolver {@code false} faz o vanilla remover o efeito, e a remocao solta a zona. */
    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.level().isClientSide || entity.tickCount % TimeStop.INTERVAL_TICKS != 0) return true;
        return TimeStop.pulse(entity);
    }

    /** Leite e totem nao devolvem o tempo: so a segunda conjuracao (ou a morte) devolve. */
    @Override
    public void fillEffectCures(Set<EffectCure> cures, MobEffectInstance instance) {
    }
}
