package com.aurorion.magia.mixin;

import com.aurorion.magia.spell.FriendlyFire;
import io.redspace.ironsspellbooks.damage.DamageSources;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A checagem do Iron's e usada tanto para escolher/colidir com alvos quanto para aplicar dano. */
@Mixin(value = DamageSources.class, remap = false)
public abstract class SpellTeamCheckMixin {
    @Inject(method = "isFriendlyFireBetween(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;)Z",
            at = @At("HEAD"), cancellable = true, require = 1)
    private static void aurorion_magia$allowSpellTargets(Entity attacker, Entity target,
                                                        CallbackInfoReturnable<Boolean> callback) {
        if (FriendlyFire.ignoresTeamsBetween(attacker, target)) callback.setReturnValue(false);
    }
}
