package com.aurorion.ethereal.ceremony;

import com.aurorion.ethereal.house.House;
import com.aurorion.ethereal.house.HouseCatalog;
import com.aurorion.ethereal.house.HouseManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/** A staff dispara a revelacao no palco; cadastrar uma casa nunca inicia uma cena no login. */
public final class CeremonyManager {
    public enum Result { OK, UNKNOWN_HOUSE, OFFLINE, ALREADY_RUNNING }

    private CeremonyManager() {}

    public static Result bind(MinecraftServer server, UUID player, ResourceLocation houseId) {
        House house = HouseCatalog.get(houseId);
        if (house == null) return Result.UNKNOWN_HOUSE;
        ServerPlayer online = server.getPlayerList().getPlayer(player);
        if (online == null || !online.isAlive()) return Result.OFFLINE;
        if (BindingRite.isBusy()) return Result.ALREADY_RUNNING;

        // Persistir antes da apresentacao: cancelamento ou desconexao nao desfazem a casa.
        HouseManager.assign(server, player, houseId);
        CeremonyData.get(server).cancel(player);
        BindingRite.start(online, house);
        return Result.OK;
    }

    public static boolean cancel(MinecraftServer server, UUID player) {
        boolean queued = CeremonyData.get(server).cancel(player);
        boolean running = BindingRite.isBinding(player);
        BindingRite.forget(player);
        return queued || running;
    }

    public static boolean isRunning(UUID player) { return BindingRite.isBinding(player); }
    public static void forget(UUID player) { BindingRite.forget(player); }
    public static void clear() { BindingRite.clear(); }
}
