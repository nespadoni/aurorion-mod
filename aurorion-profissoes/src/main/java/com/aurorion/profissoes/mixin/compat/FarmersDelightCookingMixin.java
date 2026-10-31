package com.aurorion.profissoes.mixin.compat;

import com.aurorion.profissoes.craft.CraftRoll;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Farmer's Delight, fogão e frigideira no bloco: o que termina de cozinhar sai como item no chão, então
 * a falha pode virar Peça Arruinada sem travar nada. A mão é de quem colocou o bloco.
 */
@Pseudo
@Mixin(targets = {
        "vectorwing.farmersdelight.common.block.entity.AbstractStoveBlockEntity",
        "vectorwing.farmersdelight.common.block.entity.SkilletBlockEntity"
}, remap = false)
public abstract class FarmersDelightCookingMixin {
    /** Fogão: o resultado nasce no lambda que o {@code cookAndOutputItems} passa ao {@code Optional.map}. */
    @ModifyExpressionValue(method = "lambda$cookAndOutputItems$0", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/crafting/AbstractCookingRecipe;assemble(Lnet/minecraft/world/item/crafting/SingleRecipeInput;Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack aurorion$stove(ItemStack cooked) {
        return check(cooked);
    }

    /** Frigideira no bloco. */
    @ModifyExpressionValue(method = "cookAndOutputItems", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/crafting/CampfireCookingRecipe;assemble(Lnet/minecraft/world/item/crafting/SingleRecipeInput;Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack aurorion$skillet(ItemStack cooked) {
        return check(cooked);
    }

    private ItemStack check(ItemStack cooked) {
        BlockEntity block = (BlockEntity) (Object) this;
        if (!CraftRoll.forBlock(block, cooked, 0).failed()) return cooked;
        CraftRoll.machineFailed(block);
        return CraftRoll.ruined();
    }
}
