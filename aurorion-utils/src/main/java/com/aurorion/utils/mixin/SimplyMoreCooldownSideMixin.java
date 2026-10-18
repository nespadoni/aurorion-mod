package com.aurorion.utils.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** StunEffect chama esta rotina no cliente tambem, inclusive para dummies que nao sao Player. */
@Pseudo
@Mixin(targets = "net.rosemarythyme.simplymore.util.EntityUtils", remap = false)
public abstract class SimplyMoreCooldownSideMixin {
    @Inject(method = "putAllItemsOnCooldown(Lnet/minecraft/world/entity/LivingEntity;I)V",
            at = @At("HEAD"), cancellable = true, require = 1)
    private static void aurorion_utils$serverCooldownOnly(LivingEntity entity, int ticks, CallbackInfo callback) {
        // A sincronizacao do servidor ja envia os cooldowns aos jogadores. No cliente, a rotina
        // original tenta converter o ClientLevel de qualquer mob/dummy em ServerLevel e derruba o jogo.
        if (entity.level().isClientSide()) callback.cancel();
    }
}
