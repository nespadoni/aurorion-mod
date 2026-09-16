package com.aurorion.profissoes.mixin;

import com.aurorion.profissoes.server.SpecialtyRules;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(AnvilMenu.class)
public abstract class AnvilMenuMixin extends ItemCombinerMenu {
    protected AnvilMenuMixin(MenuType<?> type, int id, Inventory inventory, ContainerLevelAccess access) { super(type, id, inventory, access); }
    @Inject(method = "createResult", at = @At("RETURN"))
    private void aurorion$result(CallbackInfo ci) {
        if (!SpecialtyRules.legalAnvil(player, inputSlots.getItem(0), resultSlots.getItem(0))) resultSlots.setItem(0, ItemStack.EMPTY);
    }
    @Inject(method = "mayPickup", at = @At("HEAD"), cancellable = true)
    private void aurorion$take(Player player, boolean hasStack, CallbackInfoReturnable<Boolean> cir) {
        if (!SpecialtyRules.legalAnvil(player, inputSlots.getItem(0), resultSlots.getItem(0))) cir.setReturnValue(false);
    }
}
