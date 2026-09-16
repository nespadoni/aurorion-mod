package com.aurorion.profissoes.mixin;

import com.aurorion.profissoes.config.ProfessionsConfig;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GrindstoneMenu.class)
public abstract class GrindstoneMenuMixin {
    @Shadow @Final private Container resultSlots;
    @Shadow @Final private Container repairSlots;
    @Inject(method = "createResult", at = @At("RETURN"))
    private void aurorion$noRepair(CallbackInfo ci) {
        if (ProfessionsConfig.enabled() && !repairSlots.getItem(0).isEmpty() && !repairSlots.getItem(1).isEmpty())
            resultSlots.setItem(0, ItemStack.EMPTY);
    }
}
