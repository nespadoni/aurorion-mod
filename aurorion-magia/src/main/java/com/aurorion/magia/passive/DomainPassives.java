package com.aurorion.magia.passive;

import com.aurorion.magia.network.MagiaNetwork;
import com.aurorion.magia.network.SpellVisualPayload;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/** Passivas sem relogio global: refinamento na conjuracao e colheita no abate. */
public final class DomainPassives {
    private static final String REAP_TIME = "aurorion_magia:ultima_colheita";

    private DomainPassives() { }

    public static int duration(LivingEntity caster, int ticks) {
        return refined(caster) ? ticks + ticks / 4 : ticks;
    }

    public static float radius(LivingEntity caster, float radius, float ceiling) {
        return Math.min(ceiling, refined(caster) ? radius * 1.25F : radius);
    }

    private static boolean refined(LivingEntity caster) {
        return caster instanceof ServerPlayer player && Passives.has(player, Passive.REFINED_DOMAIN);
    }

    public static void reap(ServerPlayer killer, LivingEntity victim) {
        if (victim == killer || !Passives.has(killer, Passive.SOUL_REAPER)
                || killer.isAlliedTo(victim) || !killer.isAlive()) return;
        long time = killer.serverLevel().getGameTime();
        var data = killer.getPersistentData();
        if (data.contains(REAP_TIME)) {
            long last = data.getLong(REAP_TIME);
            if (time >= last && time - last < 60) return;
        }
        data.putLong(REAP_TIME, time);
        killer.heal(4);
        MagicData.getPlayerMagicData(killer).addMana(20);
        MagiaNetwork.sendVisual(killer, killer, SpellVisualPayload.Kind.SOUL_REAP, 24);
    }
}
