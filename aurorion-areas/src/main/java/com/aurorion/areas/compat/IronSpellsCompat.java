package com.aurorion.areas.compat;

import com.aurorion.areas.AurorionAreas;
import com.aurorion.areas.api.AreaApi;
import com.aurorion.areas.config.AreasConfig;
import com.aurorion.areas.rules.AreaRule;
import com.aurorion.areas.server.AreaRuntime;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.*;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import java.lang.reflect.Method;

/** Optional bridge: no Iron's classes appear in signatures or class loading on servers without it. */
public final class IronSpellsCompat {
    private static Method spellId, magicData, isCasting, castingId, cancelCast;
    private static boolean reportedFailure;
    private IronSpellsCompat() {}
    public static void register() {
        if (!ModList.get().isLoaded("irons_spellbooks")) return;
        try {
            var event = Class.forName("io.redspace.ironsspellbooks.api.events.SpellPreCastEvent").asSubclass(PlayerEvent.class);
            if (!ICancellableEvent.class.isAssignableFrom(event)) throw new NoSuchMethodException("PreCast is not cancellable");
            spellId = event.getMethod("getSpellId");
            listen(event);
            var data = Class.forName("io.redspace.ironsspellbooks.api.magic.MagicData");
            magicData = data.getMethod("getPlayerMagicData", LivingEntity.class);
            isCasting = data.getMethod("isCasting");
            castingId = data.getMethod("getCastingSpellId");
            cancelCast = Class.forName("io.redspace.ironsspellbooks.api.util.Utils")
                    .getMethod("serverSideCancelCast", ServerPlayer.class);
            AurorionAreas.LOGGER.info("Areas: Iron's Spells PreCast integration registered.");
        } catch (ReflectiveOperationException | LinkageError exception) {
            AurorionAreas.LOGGER.error("Areas: incompatible Iron's Spells API. Check version; spell integration is incomplete.", exception);
        }
    }
    private static <T extends PlayerEvent> void listen(Class<T> type) {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, type, event -> {
            if (!(event.getEntity() instanceof ServerPlayer player)) return;
            boolean magicDenied = !AreaApi.allows(player, AreaRule.MAGIC);
            boolean flightDenied = !AreaApi.allows(player, AreaRule.FLIGHT);
            if (!magicDenied && !flightDenied) return;
            try {
                if (magicDenied || isFlightSpell((String) spellId.invoke(event))) {
                    ((ICancellableEvent) event).setCanceled(true);
                    AreaRuntime.denyNotice(player, magicDenied ? AreaRule.MAGIC : AreaRule.FLIGHT);
                }
            } catch (ReflectiveOperationException exception) {
                ((ICancellableEvent) event).setCanceled(true);
                report(exception);
            }
        });
    }
    public static boolean isFlightSpell(String id) { return AreasConfig.FLIGHT_SPELLS.get().contains(id); }
    /** Called only on entry into a new restriction, before the next spell tick. */
    public static void stopForbiddenCast(ServerPlayer player, boolean magicDenied, boolean flightDenied) {
        if (cancelCast == null) return;
        try {
            Object data = magicData.invoke(null, player);
            if (Boolean.TRUE.equals(isCasting.invoke(data))
                    && (magicDenied || flightDenied && isFlightSpell((String) castingId.invoke(data)))) {
                cancelCast.invoke(null, player);
            }
        } catch (ReflectiveOperationException exception) { report(exception); }
    }
    private static void report(Exception exception) {
        if (reportedFailure) return;
        reportedFailure = true;
        AurorionAreas.LOGGER.error("Areas: Iron's Spells invocation failed; verify the installed version.", exception);
    }
}
