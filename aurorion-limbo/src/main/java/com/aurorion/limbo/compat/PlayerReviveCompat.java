package com.aurorion.limbo.compat;

import com.aurorion.limbo.AurorionLimbo;
import com.aurorion.vidas.lives.LivesManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.ModList;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Compatibilidade opcional com PlayerRevive, sem colocar seu jar no classpath de compilacao. */
public final class PlayerReviveCompat {
    private static final String BLEEDING_TAG = "playerrevive:bleeding";

    private static Method getBleeding;
    private static Method isBleeding;
    private static Method revive;
    private static Method sendUpdate;
    private static boolean unavailable;

    private PlayerReviveCompat() {
    }

    /**
     * Remove um estado de sangramento que tenha atravessado a morte final ou um save antigo.
     *
     * <p>O fluxo normal do PlayerRevive ja se limpa depois de chamar {@code Player#die}. Esta
     * reconciliacao existe para login e respawn: se a execucao anterior foi interrompida entre os
     * dois passos, o jogador nao fica preso no chao dentro do Limbo.
     */
    public static void clearIfExiled(ServerPlayer player) {
        if (!LivesManager.isExiled(player.server, player.getUUID())) return;
        if (!ModList.get().isLoaded("playerrevive") || unavailable) return;

        try {
            resolve();
        } catch (ClassNotFoundException | NoSuchMethodException | LinkageError error) {
            unavailable = true;
            AurorionLimbo.LOGGER.warn("A API do PlayerRevive instalada nao e compativel.", error);
            return;
        }

        try {
            Object bleeding = getBleeding.invoke(null, player);
            boolean active = (boolean) isBleeding.invoke(bleeding);
            boolean persisted = player.getPersistentData().getBoolean(BLEEDING_TAG);
            if (!active && !persisted) return;

            revive.invoke(bleeding, player);
            player.getPersistentData().remove(BLEEDING_TAG);
            player.setForcedPose(null);
            sendUpdate.invoke(null, player);
            AurorionLimbo.LOGGER.info(
                    "Estado pendente do PlayerRevive removido de {} ao entrar no Limbo.",
                    player.getGameProfile().getName());
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException | LinkageError error) {
            AurorionLimbo.LOGGER.warn(
                    "Nao foi possivel limpar o estado do PlayerRevive para {}.",
                    player.getGameProfile().getName(), error);
        }
    }

    private static void resolve() throws ClassNotFoundException, NoSuchMethodException {
        if (getBleeding != null) return;

        Class<?> server = Class.forName("team.creative.playerrevive.server.PlayerReviveServer");
        Class<?> state = Class.forName("team.creative.playerrevive.api.IBleeding");
        getBleeding = server.getMethod("getBleeding", Player.class);
        sendUpdate = server.getMethod("sendUpdatePacket", Player.class);
        isBleeding = state.getMethod("isBleeding");
        revive = state.getMethod("revive", Player.class);
    }
}
