package com.aurorion.limbo.mixin;

import com.aurorion.vidas.lives.LivesManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Impede que um exilado entre em um segundo estado de quase-morte dentro do Limbo. */
@Pseudo
@Mixin(targets = "team.creative.playerrevive.server.ReviveEventServer", remap = false)
public abstract class PlayerReviveCompatMixin {
    @Inject(method = "isReviveActive", at = @At("HEAD"), cancellable = true, require = 0)
    private static void aurorionLimbo$disableForExiled(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof ServerPlayer player
                && LivesManager.isExiled(player.server, player.getUUID())) {
            cir.setReturnValue(false);
        }
    }
}
