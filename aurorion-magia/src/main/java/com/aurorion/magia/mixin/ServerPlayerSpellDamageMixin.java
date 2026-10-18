package com.aurorion.magia.mixin;

import com.aurorion.magia.spell.FriendlyFire;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Libera so o dano magico entre casas; golpes e flechas comuns mantem o fogo amigo do time. */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerSpellDamageMixin {
    @ModifyExpressionValue(method = "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;canHarmPlayer(Lnet/minecraft/world/entity/player/Player;)Z"),
            require = 1)
    private boolean aurorion_magia$allowMagic(boolean original, DamageSource source, float amount) {
        return original || FriendlyFire.allowsSpellDamage(source, (ServerPlayer) (Object) this);
    }
}
