package com.aurorion.profissoes.mixin.craft;

import com.aurorion.profissoes.craft.CraftRoll;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Teste de ofício na bancada (3x3), no inventário (2x2) e em todo slot de resultado de mod que herda de
 * {@link ResultSlot}. Aqui é a retirada com clique: a peça já está na mão; na falha ela zera, os
 * ingredientes se gastam como sempre (o resto do {@code onTake} do vanilla) e sai a Peça Arruinada.
 *
 * <p>O shift-click move a peça antes de chamar o {@code onTake}, então ele é testado no próprio
 * {@code quickMoveStack} ({@link CraftingQuickMoveMixin}); aqui a decisão de lá é só consumida.
 */
@Mixin(ResultSlot.class)
public abstract class ResultSlotMixin extends Slot {
    protected ResultSlotMixin(Container container, int slot, int x, int y) {
        super(container, slot, x, y);
    }

    @Inject(method = "onTake", at = @At("HEAD"))
    private void aurorion$craftCheck(Player player, ItemStack stack, CallbackInfo ci) {
        if (player instanceof ServerPlayer serverPlayer) CraftRoll.take(serverPlayer, stack);
    }

    /** Item que exige nó da Trama (o arcano, sem o Despertar I): não sai da bancada. */
    @Override
    public boolean mayPickup(Player player) {
        if (player instanceof ServerPlayer serverPlayer && hasItem()
                && !CraftRoll.allowed(serverPlayer, getItem())) {
            CraftRoll.blocked(serverPlayer, getItem());
            return false;
        }
        return super.mayPickup(player);
    }
}
