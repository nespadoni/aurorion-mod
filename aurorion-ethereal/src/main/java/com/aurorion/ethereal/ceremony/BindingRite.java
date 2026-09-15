package com.aurorion.ethereal.ceremony;

import com.aurorion.ethereal.house.House;
import com.aurorion.ethereal.house.HouseManager;
import com.aurorion.ethereal.network.RitePayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Uma apresentacao por vez. O servidor guarda o vinculo; cada espectador desenha a cena. */
public final class BindingRite {
    public static final int INTRO_TICKS = 40;
    public static final int RISE_TICKS = 60;
    public static final int GATHER_TICKS = 60;
    public static final int BURST_TICKS = 20;
    public static final int CROWN_TICKS = 180;
    public static final int REVEAL_TICK = INTRO_TICKS + RISE_TICKS + GATHER_TICKS;
    public static final int TOTAL_TICKS = REVEAL_TICK + BURST_TICKS + CROWN_TICKS;
    public static final int FADE_TICKS = 40;
    public static final int AUDIENCE_RADIUS = 96;

    private static final class Rite {
        final ServerPlayer player;
        final ServerLevel level;
        final House house;
        final Vec3 anchor;
        final List<ServerPlayer> audience = new ArrayList<>();
        int tick;

        Rite(ServerPlayer player, House house) {
            this.player = player;
            this.level = player.serverLevel();
            this.house = house;
            this.anchor = player.position();
        }
    }

    @Nullable private static Rite active;

    private BindingRite() {}

    public static boolean start(ServerPlayer player, House house) {
        if (active != null) return false;
        active = new Rite(player, house);
        for (ServerPlayer viewer : player.serverLevel().players()) syncTo(viewer);
        return true;
    }

    /** Login/tracking sao eventos, sem varrer a plateia por tick. */
    public static void syncTo(ServerPlayer viewer) {
        Rite rite = active;
        if (rite == null || viewer.level() != rite.level || rite.audience.contains(viewer)
                || !viewer.connection.hasChannel(RitePayload.TYPE)
                || viewer.distanceToSqr(rite.anchor) > AUDIENCE_RADIUS * AUDIENCE_RADIUS) return;
        rite.audience.add(viewer);
        PacketDistributor.sendToPlayer(viewer, RitePayload.start(rite.player, rite.house, rite.anchor, rite.tick));
    }

    public static boolean isBusy() { return active != null; }
    public static boolean isBinding(UUID player) { return active != null && active.player.getUUID().equals(player); }

    /** Cancelar encerra musica e efeitos de quem recebeu o inicio, mesmo se ele se afastou. */
    public static void forget(UUID player) {
        if (isBinding(player)) finish();
    }

    public static void clear() { if (active != null) finish(); }

    public static void tick(MinecraftServer server) {
        Rite rite = active;
        if (rite == null) return;
        if (server.getPlayerList().getPlayer(rite.player.getUUID()) != rite.player
                || rite.player.level() != rite.level || !rite.player.isAlive()
                || rite.player.distanceToSqr(rite.anchor) > 16
                || !rite.house.id().equals(HouseManager.houseIdOf(server, rite.player.getUUID()))) {
            finish();
            return;
        }
        if (++rite.tick == REVEAL_TICK) {
            HouseManager.announce(server, rite.player.getDisplayName(), rite.house);
        }
        if (rite.tick >= TOTAL_TICKS) finish();
    }

    private static void finish() {
        Rite rite = active;
        active = null;
        if (rite == null) return;
        RitePayload end = RitePayload.end(rite.player);
        for (ServerPlayer viewer : rite.audience) {
            if (viewer.server.getPlayerList().getPlayer(viewer.getUUID()) == viewer
                    && viewer.connection.hasChannel(RitePayload.TYPE)) {
                PacketDistributor.sendToPlayer(viewer, end);
            }
        }
    }
}
