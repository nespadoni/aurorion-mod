package com.aurorion.profissoes.mixin;

import com.aurorion.profissoes.server.SpecialtyRules;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

/**
 * Mesa de ferraria somente para ferreiros (staff criativa liberada). Vale também para menus que
 * herdam de SmithingMenu, como o upgrade de ferraria do Sophisticated Backpacks.
 *
 * O shift-click do resultado repete quickMoveStack em loop sem consultar mayPickup de novo; com
 * mods que mantêm a receita selecionada (Polymorph, mochilas), um resultado antigo podia ser
 * retirado depois de os insumos acabarem. Cada retirada revalida a receita com os insumos atuais.
 */
@Mixin(SmithingMenu.class)
public abstract class SmithingMenuMixin extends ItemCombinerMenu {
    protected SmithingMenuMixin(MenuType<?> type, int id, Inventory inventory, ContainerLevelAccess access) { super(type, id, inventory, access); }
    @Inject(method = "createResult", at = @At("RETURN"))
    private void aurorion$result(CallbackInfo ci) {
        if (!SpecialtyRules.smith(player)) resultSlots.setItem(0, ItemStack.EMPTY);
    }
    @Inject(method = "mayPickup", at = @At("HEAD"), cancellable = true)
    private void aurorion$take(Player player, boolean hasStack, CallbackInfoReturnable<Boolean> cir) {
        if (!SpecialtyRules.smith(player)) cir.setReturnValue(false);
    }
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index == getResultSlot() && !mayPickup(player, slots.get(index).hasItem())) return ItemStack.EMPTY;
        return super.quickMoveStack(player, index);
    }
}
