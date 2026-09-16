package com.aurorion.profissoes.mixin.quality;

import com.aurorion.profissoes.compat.FoodCompat;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "de.cadentem.quality_food.util.Utils", remap = false)
public abstract class CookedQualityMixin {
    @Inject(method = "useQuality", at = @At("RETURN"))
    private static void aurorion$production(BlockEntity block, ItemStack stack, Player player, CallbackInfo ci) {
        if (block.getLevel() != null && !block.getLevel().isClientSide()) FoodCompat.produced(stack, player);
    }
}
