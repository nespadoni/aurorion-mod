package com.aurorion.ethereal.compat;

import com.aurorion.ethereal.AurorionEthereal;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

import java.lang.reflect.Method;

/** Integração opcional e somente de leitura com o cofre do aurorion-economia. */
public final class HouseEconomyBridge {
    public record Snapshot(boolean available, long balance, long capacity, int vaultLevel) {
        public static final Snapshot UNAVAILABLE = new Snapshot(false, 0L, 0L, 0);
    }

    private static final Methods METHODS = resolve();

    private HouseEconomyBridge() { }

    public static Snapshot snapshot(MinecraftServer server, ResourceLocation house) {
        if (METHODS == null) return Snapshot.UNAVAILABLE;
        try {
            return new Snapshot(true,
                    (long) METHODS.balance.invoke(null, server, house),
                    (long) METHODS.capacity.invoke(null, server, house),
                    (int) METHODS.vaultLevel.invoke(null, server, house));
        } catch (ReflectiveOperationException exception) {
            AurorionEthereal.LOGGER.error("Falha ao consultar o cofre da Casa {}.", house, exception);
            return Snapshot.UNAVAILABLE;
        }
    }

    private static Methods resolve() {
        try {
            Class<?> treasury = Class.forName("com.aurorion.economia.server.HouseTreasury");
            return new Methods(
                    treasury.getMethod("balance", MinecraftServer.class, ResourceLocation.class),
                    treasury.getMethod("capacity", MinecraftServer.class, ResourceLocation.class),
                    treasury.getMethod("vaultLevel", MinecraftServer.class, ResourceLocation.class));
        } catch (ClassNotFoundException exception) {
            return null;
        } catch (ReflectiveOperationException exception) {
            AurorionEthereal.LOGGER.error("aurorion-economia foi encontrado, mas sua API de cofres e incompatível.", exception);
            return null;
        }
    }

    private record Methods(Method balance, Method capacity, Method vaultLevel) { }
}
