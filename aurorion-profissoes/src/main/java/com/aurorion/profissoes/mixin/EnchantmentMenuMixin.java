package com.aurorion.profissoes.mixin;

import com.aurorion.profissoes.server.SpecialtyRules;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import java.util.List;

@Mixin(EnchantmentMenu.class)
public abstract class EnchantmentMenuMixin {
    @Unique private Player aurorion$owner;
    @Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/inventory/ContainerLevelAccess;)V", at = @At("RETURN"))
    private void aurorion$owner(int id, Inventory inventory, ContainerLevelAccess access, CallbackInfo ci) { aurorion$owner = inventory.player; }
    @Inject(method = "getEnchantmentList", at = @At("RETURN"), cancellable = true)
    private void aurorion$levels(RegistryAccess access, ItemStack stack, int slot, int cost, CallbackInfoReturnable<List<EnchantmentInstance>> cir) {
        if (aurorion$owner != null) cir.setReturnValue(SpecialtyRules.tableOptions(aurorion$owner, cir.getReturnValue()));
    }
}
