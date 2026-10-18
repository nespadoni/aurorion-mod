package com.aurorion.magia.spell;

import com.aurorion.magia.AurorionMagia;
import com.aurorion.magia.config.MagiaConfig;
import io.redspace.ironsspellbooks.damage.DamageSources;
import io.redspace.ironsspellbooks.damage.SpellDamageSource;
import io.redspace.ironsspellbooks.entity.mobs.IMagicSummon;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Magia entre casas sem modificar o time. Os mixins liberam a checagem de aliados do Iron's
 * (incluindo colisao dos projeteis) e a checagem de time do vanilla apenas para dano de magia.
 * A fonte original e preservada: autoria, resistencias, lifesteal e cancelamentos seguem normais.
 */
public final class FriendlyFire {
    private FriendlyFire() { }

    public static boolean spares(LivingEntity caster, LivingEntity target) {
        return !MagiaConfig.SPELLS_IGNORE_TEAMS.get() && allied(caster, target);
    }

    public static boolean allied(LivingEntity caster, LivingEntity target) {
        return DamageSources.isFriendlyFireBetween(caster, target) || caster.isAlliedTo(target);
    }

    public static boolean applyDamage(Entity target, float amount, DamageSource source) {
        return DamageSources.applyDamage(target, amount, source);
    }

    /** Nao consulta a checagem do Iron's: este metodo e chamado pelo proprio mixin dela. */
    public static boolean ignoresTeamsBetween(@Nullable Entity attacker, @Nullable Entity target) {
        if (!MagiaConfig.SPELLS_IGNORE_TEAMS.get()) return false;
        Entity owner = attacker instanceof IMagicSummon summon && summon.getSummoner() != null
                ? summon.getSummoner() : attacker;
        Entity victimOwner = target instanceof IMagicSummon summon && summon.getSummoner() != null
                ? summon.getSummoner() : target;
        return owner instanceof LivingEntity caster && victimOwner instanceof LivingEntity victim
                && caster != victim && Hits.pvpAllowed(caster, victim);
    }

    /** Reconhece tambem os tipos autorais que nao usam SpellDamageSource (bigorna, prisao, execucao). */
    public static boolean allowsSpellDamage(DamageSource source, LivingEntity victim) {
        boolean magic = source instanceof SpellDamageSource || source.typeHolder().unwrapKey()
                .map(key -> key.location().getNamespace().equals(AurorionMagia.MOD_ID)).orElse(false);
        return magic && ignoresTeamsBetween(source.getEntity(), victim);
    }
}
