package com.aurorion.magia.spell;

import com.aurorion.magia.AurorionMagia;
import com.aurorion.magia.registry.MagiaEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * O impulso roubado pelo Furtum Impetus. "Forca nao desaparece. Ela apenas muda de proprietario."
 *
 * <p>Guarda so a <b>intensidade</b>: a direcao da devolucao e a da mira de quem conjura. E o que
 * torna a magia util — roubar o empurrao de alguem que vinha correndo para a esquerda e devolve-lo
 * em outra pessoa, na direcao que voce escolheu.
 */
public final class Momentum {
    private static final String KEY = AurorionMagia.MOD_ID + ":impeto";
    /** Parado tambem tem algo a roubar: o minimo garante que a devolucao sempre empurre. */
    private static final double MIN = 0.6;
    private static final double MAX = 4.0;
    private static final int STASIS_TICKS = 12;

    private Momentum() {
    }

    /**
     * Zera o movimento da vitima e devolve quanto foi tirado, ja multiplicado pelo nivel. Serve para
     * criatura, flecha, tridente, item jogado — e para o proprio conjurador (anula um knockback ou uma
     * queda em andamento).
     */
    public static double steal(Entity victim, Entity caster, int spellLevel) {
        double taken = AurorionSpell.motionOf(victim).length() * (1.4 + 0.2 * spellLevel);
        AurorionSpell.launch(victim, Vec3.ZERO);
        victim.resetFallDistance();
        if (victim != caster && victim instanceof LivingEntity living) {
            living.addEffect(new MobEffectInstance(MagiaEffects.STASIS, STASIS_TICKS, 0, false, false, true), caster);
        }
        return Math.clamp(taken, MIN, MAX);
    }

    public static void store(LivingEntity caster, double amount, int ticks) {
        caster.getPersistentData().putDouble(KEY, amount);
        caster.addEffect(new MobEffectInstance(MagiaEffects.MOMENTUM, ticks, 0, false, false, true));
    }

    /** @return a intensidade guardada, ou 0 se nao ha nada; e esvazia. */
    public static double take(LivingEntity caster) {
        double amount = caster.getPersistentData().getDouble(KEY);
        clear(caster);
        return amount;
    }

    public static boolean has(LivingEntity caster) {
        return caster.getPersistentData().contains(KEY);
    }

    public static void clear(LivingEntity caster) {
        forget(caster);
        caster.removeEffect(MagiaEffects.MOMENTUM);
    }

    /**
     * So o dado, sem mexer no efeito. E o que o listener de remocao de efeito chama: chamar
     * {@code removeEffect} de dentro dele dispararia o mesmo evento de novo.
     */
    public static void forget(LivingEntity caster) {
        caster.getPersistentData().remove(KEY);
    }
}
