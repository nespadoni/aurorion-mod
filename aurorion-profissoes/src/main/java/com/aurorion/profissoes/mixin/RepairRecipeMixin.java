package com.aurorion.profissoes.mixin;

import com.aurorion.profissoes.config.ProfessionsConfig;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RepairItemRecipe.class)
public abstract class RepairRecipeMixin {
    @Inject(method = "matches(Lnet/minecraft/world/item/crafting/CraftingInput;Lnet/minecraft/world/level/Level;)Z", at = @At("HEAD"), cancellable = true)
    private void aurorion$anvilOnly(CraftingInput input, Level level, CallbackInfoReturnable<Boolean> cir) {
        if (ProfessionsConfig.enabled()) cir.setReturnValue(false);
    }
}
