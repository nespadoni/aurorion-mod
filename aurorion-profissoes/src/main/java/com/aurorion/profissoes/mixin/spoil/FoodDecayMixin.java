package com.aurorion.profissoes.mixin.spoil;

import com.aurorion.profissoes.compat.FoodCompat;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.elcuruxa.foodspoil.util.FoodDetector", remap = false)
public abstract class FoodDecayMixin {
    @Inject(method = "getDecayRateMultiplier", at = @At("RETURN"), cancellable = true)
    private static void aurorion$decay(ItemStack stack, CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(cir.getReturnValue() * FoodCompat.decayMultiplier(stack));
    }
}
