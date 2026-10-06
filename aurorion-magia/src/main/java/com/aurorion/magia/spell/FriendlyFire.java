package com.aurorion.magia.spell;

import com.aurorion.magia.config.MagiaConfig;
import io.redspace.ironsspellbooks.api.events.SpellDamageEvent;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.damage.DamageSources;
import io.redspace.ironsspellbooks.damage.SpellDamageSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Magia entre aliados. Com {@code magiasIgnoramTime} ligado (o padrao), magia nao escolhe lado: time,
 * montaria em comum e invocacao nao poupam ninguem — de nenhuma magia, do Aurorion ou do Iron's.
 *
 * <h2>Por que nao basta tirar o filtro de aliado</h2>
 *
 * <p>Ha duas travas depois da mira, e as duas olham o <b>causador</b> do dano:
 *
 * <ul>
 *   <li>o Iron's ({@code DamageSources.applyDamage}) devolve "nao feriu" quando causador e alvo sao do
 *       mesmo time ({@code isFriendlyFireBetween});</li>
 *   <li>o vanilla ({@code ServerPlayer.hurt}) recusa dano de jogador do mesmo time com fogo amigo
 *       desligado ({@code canHarmPlayer}).</li>
 * </ul>
 *
 * <p>Entre aliados, entao, o dano sai numa fonte em que quem conjurou e so a entidade <i>direta</i>, sem
 * causador. As duas travas deixam passar, e a mensagem de morte continua nomeando quem conjurou (as
 * duas usam a entidade direta quando nao ha causador). O preco: matar um aliado com magia nao conta
 * abate para quem conjurou.
 *
 * <p>So vale onde PvP vale ({@link Hits#pvpAllowed}): a regra do servidor e a do {@code aurorion-areas}
 * continuam protegendo — sem causador, ninguem mais conferiria isso.
 */
public final class FriendlyFire {
    private FriendlyFire() {
    }

    /** {@code true} se a magia poupa o alvo por ser aliado de quem conjurou. Com a chave ligada, nunca. */
    public static boolean spares(LivingEntity caster, LivingEntity target) {
        return !MagiaConfig.SPELLS_IGNORE_TEAMS.get() && allied(caster, target);
    }

    /** Mesmo time, montaria em comum, invocacao do outro — o que o Iron's e o vanilla chamam de aliado. */
    public static boolean allied(LivingEntity caster, LivingEntity target) {
        return DamageSources.isFriendlyFireBetween(caster, target) || caster.isAlliedTo(target);
    }

    /**
     * {@link DamageSources#applyDamage} que fere aliado quando a chave manda. Devolve se o dano entrou,
     * como o original — as magias que curam, puxam ou silenciam so quando acertam dependem disso.
     */
    public static boolean applyDamage(Entity target, float amount, DamageSource source) {
        return DamageSources.applyDamage(target, amount, source(source, target));
    }

    /** A fonte de dano que de fato entra em {@code target}: a original, ou a sem causador entre aliados. */
    public static DamageSource source(DamageSource source, Entity target) {
        if (source instanceof Unbound || !(target instanceof LivingEntity victim)
                || !(source.getEntity() instanceof LivingEntity caster) || caster == victim
                || !bypasses(caster, victim)) return source;
        Entity direct = source.getDirectEntity() != null ? source.getDirectEntity() : caster;
        if (source instanceof SpellDamageSource spell) return Unbound.of(direct, spell);
        return new DamageSource(source.typeHolder(), direct, null);
    }

    /**
     * Magias do Iron's (e de qualquer addon) que ferem aliado: o evento chega antes da trava de time
     * do Iron's. Cancela o dano original e aplica o mesmo valor pela fonte sem causador.
     */
    public static void onSpellDamage(SpellDamageEvent event) {
        SpellDamageSource source = event.getSpellDamageSource();
        LivingEntity victim = event.getEntity();
        if (source instanceof Unbound || victim.level().isClientSide
                || !(source.getEntity() instanceof LivingEntity caster) || caster == victim
                || !bypasses(caster, victim)) return;
        event.setCanceled(true);
        DamageSources.applyDamage(victim, event.getAmount(), Unbound.of(source.getDirectEntity() != null
                ? source.getDirectEntity() : caster, source));
    }

    /** A chave esta ligada, os dois sao aliados (as travas recusariam) e ali vale PvP. */
    private static boolean bypasses(LivingEntity caster, LivingEntity victim) {
        return MagiaConfig.SPELLS_IGNORE_TEAMS.get() && allied(caster, victim) && Hits.pvpAllowed(caster, victim);
    }

    /** Fonte de dano de magia sem causador: quem conjurou vai como entidade direta. */
    static final class Unbound extends SpellDamageSource {
        private Unbound(Entity direct, AbstractSpell spell) {
            super(direct, null, null, spell);
        }

        static Unbound of(Entity direct, SpellDamageSource original) {
            Unbound unbound = new Unbound(direct, original.spell());
            if (original.getIFrames() >= 0) unbound.setIFrames(original.getIFrames());
            if (original.getFireTime() > 0) unbound.setFireTicks(original.getFireTime());
            if (original.getFreezeTicks() > 0) unbound.setFreezeTicks(original.getFreezeTicks());
            if (original.getLifestealPercent() > 0) unbound.setLifestealPercent(original.getLifestealPercent());
            return unbound;
        }
    }
}
