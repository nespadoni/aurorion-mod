package com.aurorion.profissoes.mixin.craft;

import com.aurorion.profissoes.craft.CraftRoll;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Shift-click do resultado da bancada e do 2x2: o vanilla move a peça para o inventário e só depois
 * chama o {@code onTake}, então o teste vem antes, no começo do {@code quickMoveStack}. Uma decisão por
 * peça (o shift-click repete em loop); a falha gasta os ingredientes e para o loop. Ver
 * {@link CraftRoll#quickMove}.
 */
@Mixin({CraftingMenu.class, InventoryMenu.class})
public abstract class CraftingQuickMoveMixin {
    private static final int RESULT_SLOT = 0;

    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
    private void aurorion$craftCheck(Player player, int index, CallbackInfoReturnable<ItemStack> cir) {
        if (index != RESULT_SLOT || !(player instanceof ServerPlayer serverPlayer)) return;
        Slot slot = ((AbstractContainerMenu) (Object) this).slots.get(RESULT_SLOT);
        if (!slot.hasItem()) return;
        ItemStack result = slot.getItem();
        ItemStack answer = CraftRoll.quickMove(serverPlayer, result, () -> slot.onTake(player, result.copy()));
        if (answer != null) cir.setReturnValue(answer);
    }

    @Inject(method = "quickMoveStack", at = @At("RETURN"))
    private void aurorion$craftDone(Player player, int index, CallbackInfoReturnable<ItemStack> cir) {
        if (index == RESULT_SLOT && player instanceof ServerPlayer serverPlayer) {
            CraftRoll.finishQuickMove(serverPlayer, !cir.getReturnValue().isEmpty());
        }
    }
}
