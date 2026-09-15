package com.aurorion.areas.api;

import com.aurorion.areas.config.AreasConfig;
import com.aurorion.areas.data.AreaData;
import com.aurorion.areas.rules.AreaRule;
import com.aurorion.areas.server.AreaRuntime;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import java.util.UUID;

/** Server-thread API for spells, vehicles and future professions. An ALLOW never grants an ability. */
public final class AreaApi {
    private AreaApi() {}
    public static boolean bypass(ServerPlayer player) {
        return player.isSpectator() || player instanceof net.neoforged.neoforge.common.util.FakePlayer
                || player.getTags().contains("aurorion_areas_npc") || (AreasConfig.CREATIVE_BYPASS.get()
                && player.isCreative() && player.hasPermissions(2));
    }
    public static boolean allows(ServerPlayer player, AreaRule rule) {
        return !AreasConfig.ENABLED.get() || bypass(player) || AreaRuntime.rules(player).allows(rule);
    }
    public static boolean allows(ServerPlayer player, String key) {
        return !AreasConfig.ENABLED.get() || bypass(player)
                || allowsAt(player.serverLevel(), player.getX(), player.getY(), player.getZ(), player.getUUID(), key);
    }
    /** Raw location policy, including a character's explicit exceptions but without creative bypass. */
    public static boolean allowsAt(ServerLevel level, double x, double y, double z, @Nullable UUID actor, String key) {
        return !AreasConfig.ENABLED.get()
                || AreaData.get(level.getServer()).allows(level.dimension().location(), x, y, z, actor, key);
    }
}
