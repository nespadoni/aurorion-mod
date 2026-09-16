package com.aurorion.profissoes.mixin;

import com.aurorion.profissoes.compat.FoodCompat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Fogueira nao identifica o cozinheiro; finalizacao especializada ocorre pela UI. */
@Mixin(CampfireBlockEntity.class)
public abstract class CampfireMixin {
    @ModifyArg(method = "cookTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/Containers;dropItemStack(Lnet/minecraft/world/level/Level;DDDLnet/minecraft/world/item/ItemStack;)V"), index = 4)
    private static ItemStack aurorion$commonCooking(ItemStack cooked) {
        FoodCompat.produced(cooked, null);
        return cooked;
    }
}
