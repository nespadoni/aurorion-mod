package com.aurorion.profissoes.mixin;

import com.aurorion.profissoes.server.SpecialtyRules;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
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
    @Shadow @Final public int[] costs;
    @Shadow @Final public int[] enchantClue;
    @Shadow @Final public int[] levelClue;
    @Unique private Player aurorion$owner;

    @Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/inventory/ContainerLevelAccess;)V", at = @At("RETURN"))
    private void aurorion$owner(int id, Inventory inventory, ContainerLevelAccess access, CallbackInfo ci) { aurorion$owner = inventory.player; }

    @Inject(method = "slotsChanged", at = @At("TAIL"))
    private void aurorion$lockThirdOption(net.minecraft.world.Container inventory, CallbackInfo ci) {
        if (aurorion$owner == null || SpecialtyRules.canUseTableOption(aurorion$owner, 2)) return;
        costs[2] = 0;
        enchantClue[2] = -1;
        levelClue[2] = -1;
        if (!aurorion$owner.level().isClientSide()) ((EnchantmentMenu)(Object)this).broadcastChanges();
    }

    @Inject(method = "clickMenuButton", at = @At("HEAD"), cancellable = true)
    private void aurorion$validateThirdOption(Player player, int option, CallbackInfoReturnable<Boolean> cir) {
        if (SpecialtyRules.canUseTableOption(player, option)) return;
        if (option == 2) player.displayClientMessage(
                Component.translatable("message.aurorion_profissoes.enchant.third_option_requires_arcanist"), true);
        cir.setReturnValue(false);
    }

    @Inject(method = "getEnchantmentList", at = @At("RETURN"), cancellable = true)
    private void aurorion$levels(RegistryAccess access, ItemStack stack, int slot, int cost, CallbackInfoReturnable<List<EnchantmentInstance>> cir) {
        if (aurorion$owner != null) cir.setReturnValue(SpecialtyRules.tableOptions(aurorion$owner, cir.getReturnValue()));
    }
}
