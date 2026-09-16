package com.aurorion.profissoes.mixin;

import com.aurorion.profissoes.config.ProfessionsConfig;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BrewingStandBlockEntity.class)
public abstract class BrewingStandMixin {
    @Inject(method = "isBrewable", at = @At("HEAD"), cancellable = true)
    private static void aurorion$specialist(PotionBrewing brewing, NonNullList<ItemStack> items, CallbackInfoReturnable<Boolean> cir) {
        if (ProfessionsConfig.enabled() && (items.get(3).is(Items.REDSTONE) || items.get(3).is(Items.GLOWSTONE_DUST))) cir.setReturnValue(false);
    }
}
