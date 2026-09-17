package com.aurorion.ethereal.compat;

import com.aurorion.ethereal.AurorionEthereal;
import net.minecraft.server.MinecraftServer;

import java.lang.reflect.Method;
import java.util.UUID;

/** Integração opcional com vidas sem tornar o mod Ethereal dependente do aurorion-vidas. */
public final class HouseLivesBridge {
    private static final Methods METHODS = resolve();

    private HouseLivesBridge() { }

    public static boolean available() {
        return METHODS != null;
    }

    public static int livesOf(MinecraftServer server, UUID player) {
        if (METHODS == null) return 0;
        try {
            return (int) METHODS.livesOf.invoke(null, server, player);
        } catch (ReflectiveOperationException exception) {
            AurorionEthereal.LOGGER.error("Falha ao consultar vidas de {}.", player, exception);
            return 0;
        }
    }

    public static int maxLives() {
        if (METHODS == null) return 0;
        try {
            return (int) METHODS.maxLives.invoke(null);
        } catch (ReflectiveOperationException exception) {
            AurorionEthereal.LOGGER.error("Falha ao consultar o máximo de vidas.", exception);
            return 0;
        }
    }

    /** Retorna verdadeiro apenas quando uma vida foi efetivamente acrescentada. */
    public static boolean addOne(MinecraftServer server, UUID player) {
        if (METHODS == null) return false;
        int before = livesOf(server, player);
        if (before >= maxLives()) return false;
        try {
            int after = (int) METHODS.addLives.invoke(null, server, player, 1);
            return after == before + 1;
        } catch (ReflectiveOperationException exception) {
            AurorionEthereal.LOGGER.error("Falha ao conceder uma vida a {}.", player, exception);
            return false;
        }
    }

    private static Methods resolve() {
        try {
            Class<?> manager = Class.forName("com.aurorion.vidas.lives.LivesManager");
            return new Methods(
                    manager.getMethod("livesOf", MinecraftServer.class, UUID.class),
                    manager.getMethod("maxLives"),
                    manager.getMethod("addLives", MinecraftServer.class, UUID.class, int.class));
        } catch (ClassNotFoundException exception) {
            return null;
        } catch (ReflectiveOperationException exception) {
            AurorionEthereal.LOGGER.error("aurorion-vidas foi encontrado, mas sua API é incompatível.", exception);
            return null;
        }
    }

    private record Methods(Method livesOf, Method maxLives, Method addLives) { }
}
