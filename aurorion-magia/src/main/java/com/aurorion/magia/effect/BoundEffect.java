package com.aurorion.magia.effect;

import com.aurorion.magia.registry.MagiaEffects;
import com.aurorion.magia.spell.Binding;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.EffectCure;

import java.util.Set;

/**
 * Vinculum Carnificis: a corrente invisivel ate a ancora.
 *
 * <p>A conferencia de distancia roda aqui, no tick que o vanilla ja faz da entidade presa, 5 vezes
 * por segundo — e so em quem esta preso. Nao ha lista global de presos varrida por tick.
 *
 * <p>A corrente dura ate {@code Binding.MAX_DURATION}, ate alguem conjurar o Vinculum de novo no preso,
 * ate ele morrer, ate o dono sair do jogo ou ate o preso pisar num lugar sem PvP (esses dois ultimos
 * pelo retorno de {@link Binding#enforce}). O intervalo e conferido no tick da entidade, e nao na
 * duracao: correntes antigas, salvas como infinitas antes do prazo existir, tem duracao parada em
 * {@code -1} — e essas se abrem no primeiro tick.
 */
public final class BoundEffect extends MobEffect {
    public static final int INTERVAL = 4;

    public BoundEffect() {
        super(MobEffectCategory.HARMFUL, 0x8C1020);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.level().isClientSide || entity.tickCount % INTERVAL != 0) return true;
        // Corrente antiga, salva sem prazo antes dele existir: se abre agora, em vez de durar para sempre.
        MobEffectInstance instance = entity.getEffect(MagiaEffects.BOUND);
        if (instance != null && instance.isInfiniteDuration()) return false;
        return Binding.enforce(entity);
    }

    /** Leite e totem nao abrem a corrente: quem prendeu e quem solta. */
    @Override
    public void fillEffectCures(Set<EffectCure> cures, MobEffectInstance instance) {
    }
}
