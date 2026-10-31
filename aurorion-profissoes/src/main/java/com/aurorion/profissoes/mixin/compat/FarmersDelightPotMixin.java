package com.aurorion.profissoes.mixin.compat;

import com.aurorion.profissoes.craft.CraftRoll;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Farmer's Delight, panela: a refeição pronta ({@code CookingPotRecipe.assemble} dentro de
 * {@code processCooking}, que só roda quando o tempo de cozimento acabou) passa pelo teste com a ficha de
 * quem colocou a panela. Na falha sai vazio: os ingredientes se gastam e o slot da refeição não recebe
 * nada (uma Peça Arruinada ali travaria a panela).
 */
@Pseudo
@Mixin(targets = "vectorwing.farmersdelight.common.block.entity.CookingPotBlockEntity", remap = false)
public abstract class FarmersDelightPotMixin {
    @ModifyExpressionValue(method = "processCooking", require = 0, at = @At(value = "INVOKE",
            target = "Lvectorwing/farmersdelight/common/crafting/CookingPotRecipe;assemble(Lnet/neoforged/neoforge/items/wrapper/RecipeWrapper;Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack aurorion$craftCheck(ItemStack meal) {
        BlockEntity pot = (BlockEntity) (Object) this;
        if (!CraftRoll.forBlock(pot, meal, 0).failed()) return meal;
        CraftRoll.machineFailed(pot);
        return ItemStack.EMPTY;
    }
}
