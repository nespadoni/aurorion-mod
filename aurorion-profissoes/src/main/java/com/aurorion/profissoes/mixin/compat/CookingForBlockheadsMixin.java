package com.aurorion.profissoes.mixin.compat;

import com.aurorion.profissoes.craft.CraftRoll;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Cooking for Blockheads, livro de receitas da cozinha: cada prato sai de {@code CraftingOperation.craft},
 * que já gastou os ingredientes, dentro do {@code KitchenMenu.craft} (que repete por peça quando a pessoa
 * faz a pilha inteira). Testa com a ficha de quem está na cozinha, uma vez por prato: na falha o prato não
 * sai e vem a Peça Arruinada; no crítico de CD alta, obra-prima.
 */
@Pseudo
@Mixin(targets = "net.blay09.mods.cookingforblockheads.menu.KitchenMenu", remap = false)
public abstract class CookingForBlockheadsMixin {
    @Shadow @Final private Player player;

    @ModifyExpressionValue(method = "craft", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/blay09/mods/cookingforblockheads/crafting/CraftingOperation;craft(Lnet/minecraft/world/inventory/AbstractContainerMenu;Lnet/minecraft/core/RegistryAccess;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack aurorion$craftCheck(ItemStack dish) {
        if (!(player instanceof ServerPlayer serverPlayer) || dish.isEmpty()) return dish;
        CraftRoll.Decision decision = CraftRoll.forPlayer(serverPlayer, dish, 0);
        if (decision.failed() || decision == CraftRoll.Decision.BLOCKED) {
            CraftRoll.failed(serverPlayer, decision == CraftRoll.Decision.BLOCKED ? CraftRoll.Decision.FAIL : decision);
            return ItemStack.EMPTY;
        }
        if (decision == CraftRoll.Decision.CRIT) CraftRoll.masterpiece(serverPlayer, dish);
        return dish;
    }
}
