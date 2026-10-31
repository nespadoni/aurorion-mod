package com.aurorion.profissoes.mixin.compat;

import com.aurorion.profissoes.craft.CraftRoll;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

/**
 * Farmer's Delight com o jogador na mão: a tábua de corte (faca no item) e a frigideira segurada. Testa
 * com a ficha dele, como a bancada: na falha sai a Peça Arruinada no lugar do resultado; no crítico de
 * obra de CD alta, obra-prima.
 */
@Pseudo
@Mixin(targets = {
        "vectorwing.farmersdelight.common.block.entity.CuttingBoardBlockEntity",
        "vectorwing.farmersdelight.common.item.SkilletItem"
}, remap = false)
public abstract class FarmersDelightHandMixin {
    /** Tábua: {@code rollResults} (receita com chance) dentro do lambda do {@code processStoredItemUsingTool}. */
    @ModifyExpressionValue(method = "lambda$processStoredItemUsingTool$2", require = 0, at = @At(value = "INVOKE",
            target = "Lvectorwing/farmersdelight/common/crafting/CuttingBoardRecipe;rollResults(Lnet/minecraft/util/RandomSource;ILnet/neoforged/neoforge/items/wrapper/RecipeWrapper;)Ljava/util/List;"))
    private List<ItemStack> aurorion$cut(List<ItemStack> results, @Local(argsOnly = true) Player player) {
        if (!(player instanceof ServerPlayer serverPlayer) || results.isEmpty()) return results;
        ItemStack main = results.get(0);
        CraftRoll.Decision decision = CraftRoll.forPlayer(serverPlayer, main, 0);
        if (decision.failed() || decision == CraftRoll.Decision.BLOCKED) {
            CraftRoll.failed(serverPlayer, decision == CraftRoll.Decision.BLOCKED ? CraftRoll.Decision.FAIL : decision);
            return List.of();
        }
        if (decision == CraftRoll.Decision.CRIT) CraftRoll.masterpiece(serverPlayer, main);
        return results;
    }

    /** Frigideira na mão (lambda estático): o resultado vai para o inventário, ou cai no chão. */
    @ModifyExpressionValue(method = "lambda$finishUsingItem$0", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/crafting/CampfireCookingRecipe;assemble(Lnet/minecraft/world/item/crafting/SingleRecipeInput;Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/world/item/ItemStack;"))
    private static ItemStack aurorion$skillet(ItemStack cooked, @Local(argsOnly = true) Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) return cooked;
        CraftRoll.Decision decision = CraftRoll.forPlayer(serverPlayer, cooked, 0);
        if (decision.failed() || decision == CraftRoll.Decision.BLOCKED) {
            CraftRoll.failed(serverPlayer, decision == CraftRoll.Decision.BLOCKED ? CraftRoll.Decision.FAIL : decision);
            return ItemStack.EMPTY;
        }
        if (decision == CraftRoll.Decision.CRIT) CraftRoll.masterpiece(serverPlayer, cooked);
        return cooked;
    }
}
