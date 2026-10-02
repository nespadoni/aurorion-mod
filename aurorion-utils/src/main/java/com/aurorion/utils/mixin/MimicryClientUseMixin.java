package com.aurorion.utils.mixin;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Simply More 1.3.0_alpha casts Level to ServerLevel before checking the side. */
@Pseudo
@Mixin(targets = "net.rosemarythyme.simplymore.item.uniques.mimicry.MimicryItem", remap = false)
public abstract class MimicryClientUseMixin {
    @Inject(method = "use(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResultHolder;",
            at = @At("HEAD"), cancellable = true, require = 1)
    private void aurorion_utils$predictUse(Level level, Player player, InteractionHand hand,
                                          CallbackInfoReturnable<InteractionResultHolder<ItemStack>> callback) {
        if (level.isClientSide()) {
            // Prediction only: the original server method still checks unlocking, cooldown and damage.
            player.startUsingItem(hand);
            callback.setReturnValue(InteractionResultHolder.consume(player.getItemInHand(hand)));
        }
    }
}
