package com.aurorion.profissoes.mixin.spoil;

import com.aurorion.profissoes.compat.FoodCompat;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

@Pseudo
@Mixin(targets = "com.elcuruxa.foodspoil.util.FoodDetector", remap = false)
public abstract class FoodDecayMixin {
    /** {@code float getDecayRateMultiplier(ItemStack)} (FoodSpoil 1.1.7): sem CallbackInfoReturnable nem boxing por chamada. */
    @ModifyReturnValue(method = "getDecayRateMultiplier", at = @At("RETURN"))
    private static float aurorion$decay(float original, ItemStack stack) {
        return original * FoodCompat.decayMultiplier(stack);
    }
}
