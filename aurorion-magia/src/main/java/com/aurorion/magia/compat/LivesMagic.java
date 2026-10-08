package com.aurorion.magia.compat;

import com.aurorion.magia.AurorionMagia;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;
import java.util.UUID;

/** Ponte opcional para o mesmo caminho de /vidas dar e /vidas tirar, sem executar comandos. */
public final class LivesMagic {
    private static boolean initialized;
    private static Method livesOf, addLives, maxLives;

    private LivesMagic() { }

    public static synchronized boolean available() {
        if (!initialized) {
            initialized = true;
            if (!ModList.get().isLoaded("aurorion_vidas")) return false;
            try {
                Class<?> manager = Class.forName("com.aurorion.vidas.lives.LivesManager");
                livesOf = manager.getMethod("livesOf", MinecraftServer.class, UUID.class);
                addLives = manager.getMethod("addLives", MinecraftServer.class, UUID.class, int.class);
                maxLives = manager.getMethod("maxLives");
            } catch (ReflectiveOperationException | LinkageError error) {
                addLives = null;
                AurorionMagia.LOGGER.error("Magias de vida: API de aurorion-vidas indisponivel.", error);
            }
        }
        return addLives != null;
    }

    public static boolean canChange(ServerPlayer target, int delta) {
        if (!available()) return false;
        try {
            int current = (int) livesOf.invoke(null, target.server, target.getUUID());
            // Morto definitivo nao volta por magia: LivesManager tambem garante essa regra na escrita.
            if (com.aurorion.core.character.CharacterData.get(target.server).isDead(target.getUUID())) return false;
            return delta < 0 ? current > 0 : current < (int) maxLives.invoke(null);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Falha ao consultar vidas para magia", error);
        }
    }

    public static boolean change(ServerPlayer target, int delta) {
        if (!canChange(target, delta)) return false;
        try {
            int before = (int) livesOf.invoke(null, target.server, target.getUUID());
            int after = (int) addLives.invoke(null, target.server, target.getUUID(), delta);
            return after != before;
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Falha ao ajustar vidas por magia", error);
        }
    }
}
