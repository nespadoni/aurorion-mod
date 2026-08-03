package com.aurorion.utils.freeze;

import com.aurorion.utils.entity.FreezeAnchorEntity;
import com.aurorion.utils.entity.ModEntities;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Congelar = montar o jogador numa {@link FreezeAnchorEntity} parada. Deliberadamente separado da
 * abducao: um {@code /unfreeze} nunca deve conseguir desmontar alguem no meio de uma abducao (que
 * usa sua propria {@code AbductionBeamEntity} como montaria) — por isso a checagem de tipo aqui e
 * exata (o {@code EntityType}, nao {@code instanceof}), nunca pega a ancora de outra feature por
 * engano.
 */
public final class FreezeManager {
    private FreezeManager() {
    }

    public static boolean isFrozen(ServerPlayer player) {
        return player.getVehicle() != null && player.getVehicle().getType() == ModEntities.FREEZE_ANCHOR.get();
    }

    /** @return false se o jogador ja estava congelado (idempotente, seguro pra chamar em lote). */
    public static boolean freeze(ServerPlayer player) {
        if (isFrozen(player)) return false;

        ServerLevel level = player.serverLevel();
        FreezeAnchorEntity anchor = new FreezeAnchorEntity(ModEntities.FREEZE_ANCHOR.get(), level);
        anchor.setPos(player.getX(), player.getY(), player.getZ());
        level.addFreshEntity(anchor);
        player.startRiding(anchor, true);
        return true;
    }

    /** @return false se o jogador nao estava congelado por este comando. */
    public static boolean unfreeze(ServerPlayer player) {
        if (!isFrozen(player)) return false;

        FreezeAnchorEntity anchor = (FreezeAnchorEntity) player.getVehicle();
        player.stopRiding();
        if (anchor != null) anchor.discard();
        return true;
    }
}
