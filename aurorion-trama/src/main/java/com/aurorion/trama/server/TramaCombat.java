package com.aurorion.trama.server;

import com.aurorion.trama.skill.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.*;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import static com.aurorion.trama.skill.Rating.*;

final class TramaCombat {
    private TramaCombat() {}
    static Rating family(DamageSource source) {
        // Magic must win over projectile flags: never double-apply arrow and spell bonuses.
        if (source.is(TramaTags.MAGIC)) return MAG;
        if (source.is(DamageTypeTags.IS_PROJECTILE) && source.is(TramaTags.PHYSICAL)) return RNG;
        if (source.is(DamageTypes.PLAYER_ATTACK)) return MEL;
        return null;
    }
    static String group(DamageSource source) {
        if (source.is(TramaTags.MAGIC)) return "magic";
        if (source.is(DamageTypeTags.IS_FIRE)) return "fire";
        if (source.is(DamageTypeTags.IS_EXPLOSION)) return "explosion";
        return "";
    }
    static void before(LivingDamageEvent.Pre event) {
        if (event.getEntity().level().isClientSide() || event.getNewDamage() <= 0) return;
        var source = event.getSource();
        double amount = event.getNewDamage();
        var family = family(source);
        if (family != null && source.getEntity() instanceof ServerPlayer attacker && TramaRuntime.eligible(attacker)) {
            var s = TramaRuntime.state(attacker); var b = s.build;
            var effects = b.effects(TramaRuntime.context(attacker,s));
            double extra = 0; long time = TramaRuntime.now(attacker);
            var target = event.getEntity();
            if (family == MEL && b.has("ID7") && target.getHealth() >= target.getMaxHealth()) extra += .04;
            if (family == RNG && b.has("VP7") && target.getHealth() >= target.getMaxHealth()) extra += .04;
            if (b.has("NC4") && target.getActiveEffects().stream().anyMatch(e -> e.getEffect().is(TramaTags.DEBUFFS))) extra += .03;
            if (family == MEL && b.has("VT7") && s.exitedSprint && !s.sprint && time-s.stoppedSince <= 40 && time >= s.tailwindReady) {
                extra += .03; s.tailwindReady = time+100;
            }
            if (family == MEL && b.has("BVI7") && !s.pursuitConsumed && s.sprint && time-s.sprintSince >= 60 && time >= s.pursuitReady) {
                extra += .04; s.pursuitReady = time+160; s.pursuitConsumed = true;
            }
            amount *= 1+b.damage(effects,family,extra);
        }
        if (event.getEntity() instanceof ServerPlayer target && TramaRuntime.eligible(target)) {
            var s = TramaRuntime.state(target); var b = s.build;
            var effects = b.effects(TramaRuntime.context(target,s));
            long time = TramaRuntime.now(target);
            String group = group(source);
            double reduction = 0;
            boolean magic = source.is(TramaTags.MAGIC), physical = source.is(TramaTags.PHYSICAL);
            if (magic) {
                reduction += effects.get(MR);
                if (b.has("NV4") && time < s.magicWardUntil) reduction += .03;
            }
            if (physical) {
                reduction += effects.physicalReduction;
                if (source.is(DamageTypeTags.IS_PROJECTILE)) reduction += effects.projectileReduction;
            }
            if (source.is(TramaTags.ENVIRONMENT)) reduction += effects.get(ENV);
            if (!group.isEmpty() && (b.has("SA7") || b.has("SA9"))) {
                if (time < s.adaptationUntil && group.equals(s.adaptation)) reduction += b.has("SA9") ? .06 : .04;
                else if (b.has("SA9") && time >= s.adaptationReady) amount *= 1.03;
            }
            boolean shell = b.has("IO7") && time-s.lastDamage >= 120 && time >= s.shellReady;
            if (shell) { reduction += .08; s.shellReady = time+200; }
            // Shared resistance cap prevents environmental and spell classifications stacking past 12%.
            reduction = Build.clamp(reduction,0,magic ? .12 : source.is(TramaTags.ENVIRONMENT) ? .08 : .12);
            amount *= 1-reduction;
        }
        event.setNewDamage((float)Math.max(0,amount));
    }
    static void after(LivingDamageEvent.Post event) {
        if (event.getEntity().level().isClientSide() || event.getNewDamage() <= 0) return;
        var source = event.getSource();
        if (source.getEntity() instanceof ServerPlayer attacker && TramaRuntime.eligible(attacker))
            TramaRuntime.combat(attacker,TramaRuntime.state(attacker));
        if (!(event.getEntity() instanceof ServerPlayer target) || !TramaRuntime.eligible(target)) return;
        var s = TramaRuntime.state(target); var b = s.build; long time = TramaRuntime.now(target);
        TramaRuntime.combat(target,s); s.lastDamage = time;
        if ((b.has("VA4") || b.has("VA9")) && time >= s.evasionReady) {
            s.evasionUntil = time+40; s.evasionReady = time+160;
        }
        if (source.is(TramaTags.MAGIC)) {
            if (b.has("NV4") && time >= s.magicWardReady) { s.magicWardUntil = time+80; s.magicWardReady = time+200; }
            if (b.has("BNA7") && time >= s.magicStepReady) { s.magicStepUntil = time+60; s.magicStepReady = time+200; }
        }
        String group = group(source);
        if (!group.isEmpty() && (b.has("SA7") || b.has("SA9")) && time >= s.adaptationReady) {
            s.adaptation = group; s.adaptationUntil = time+(b.has("SA9") ? 120 : 100); s.adaptationReady = time+240;
        }
        TramaAttributes.apply(target,b.effects(TramaRuntime.context(target,s)));
    }
}
