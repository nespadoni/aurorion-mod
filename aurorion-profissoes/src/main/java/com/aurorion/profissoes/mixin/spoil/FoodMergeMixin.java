package com.aurorion.profissoes.mixin.spoil;

import com.aurorion.profissoes.compat.FoodCompat;
import com.aurorion.profissoes.config.ProfessionsConfig;
import net.neoforged.neoforge.event.ItemStackedOnOtherEvent;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.elcuruxa.foodspoil.events.InventoryMergeHandler", remap = false)
public abstract class FoodMergeMixin {
    @Inject(method = "onItemStackedOnOther", at = @At("HEAD"), cancellable = true)
    private static void aurorion$separateBatches(ItemStackedOnOtherEvent event, CallbackInfo ci) {
        if (ProfessionsConfig.enabled() && !FoodCompat.samePreparation(event.getCarriedItem(), event.getStackedOnItem())) ci.cancel();
    }
}
