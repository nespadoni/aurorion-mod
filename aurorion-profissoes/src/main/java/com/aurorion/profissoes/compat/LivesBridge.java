package com.aurorion.profissoes.compat;

import com.aurorion.core.character.CharacterData;
import com.aurorion.profissoes.AurorionProfissoes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;
import java.util.UUID;

/** Consulta o contador de vidas sem tornar aurorion-vidas obrigatorio para as profissoes. */
public final class LivesBridge {
    private static boolean initialized;
    private static Method livesOf, maxLives, setLives;

    private LivesBridge() { }

    private static synchronized void init() {
        if (initialized) return;
        initialized = true;
        if (!ModList.get().isLoaded("aurorion_vidas")) return;
        try {
            Class<?> manager = Class.forName("com.aurorion.vidas.lives.LivesManager");
            livesOf = manager.getMethod("livesOf", MinecraftServer.class, UUID.class);
            maxLives = manager.getMethod("maxLives");
            setLives = manager.getMethod("setLives", MinecraftServer.class, UUID.class, int.class);
        } catch (ReflectiveOperationException | LinkageError error) {
            livesOf = null;
            AurorionProfissoes.LOGGER.warn("Nao foi possivel integrar a cura com aurorion-vidas.", error);
        }
    }

    public static boolean missingLives(ServerPlayer target) {
        if (!initialized) init();
        if (livesOf == null || CharacterData.get(target.server).isDead(target.getUUID())) return false;
        try {
            return (int) livesOf.invoke(null, target.server, target.getUUID()) < (int) maxLives.invoke(null);
        } catch (ReflectiveOperationException error) {
            livesOf = null;
            AurorionProfissoes.LOGGER.warn("Falha ao consultar vidas para cura.", error);
            return false;
        }
    }

    public static void restore(ServerPlayer target) {
        if (!missingLives(target)) return;
        try {
            setLives.invoke(null, target.server, target.getUUID(), (int) maxLives.invoke(null));
        } catch (ReflectiveOperationException error) {
            livesOf = null;
            AurorionProfissoes.LOGGER.warn("Falha ao restaurar vidas do alvo.", error);
        }
    }
}
