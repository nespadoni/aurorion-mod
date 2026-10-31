package com.aurorion.profissoes.mixin.compat;

import com.aurorion.profissoes.craft.CraftRoll;
import com.aurorion.profissoes.data.Profession;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mesas do Apotheosis (do arcanista, que rola com vantagem nelas). Cada uma falha do jeito que faz sentido
 * para ela, sem destruir equipamento à toa:
 * <ul>
 *   <li><b>reforjar</b>: a reforja não pega — materiais e XP se gastam (o resto do {@code onTake} do mod) e o
 *   item original volta sem mudança;</li>
 *   <li><b>lapidar</b>: as gemas se gastam e a lapidada não sai (vem a Peça Arruinada);</li>
 *   <li><b>aumentar afixo</b>: o custo já foi pago quando o afixo seria aplicado; na falha ele não sobe.</li>
 * </ul>
 * O desmonte (salvage) fica de fora: não é fabricação.
 */
@Pseudo
@Mixin(targets = {
        "dev.shadowsoffire.apotheosis.affix.reforging.ReforgingMenu$ReforgingResultSlot",
        "dev.shadowsoffire.apotheosis.socket.gem.cutting.GemCuttingMenu",
        "dev.shadowsoffire.apotheosis.affix.augmenting.AugmentingMenu"
}, remap = false)
public abstract class ApotheosisMixin {
    private static final int MAIN_SLOT = 0;

    /** Reforja: a peça reforjada já está na mão; o slot 0 ainda tem o item original (o mod esvazia logo depois). */
    @Inject(method = "onTake(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;)V", at = @At("HEAD"), require = 0)
    private void aurorion$reforge(Player player, ItemStack stack, CallbackInfo ci) {
        if (!(player instanceof ServerPlayer serverPlayer) || stack.isEmpty()) return;
        CraftRoll.Decision decision = CraftRoll.forPlayer(serverPlayer, stack, 0, Profession.ARCANIST);
        if (decision.failed() || decision == CraftRoll.Decision.BLOCKED) {
            ItemStack original = serverPlayer.containerMenu.getSlot(MAIN_SLOT).getItem().copy();
            stack.setCount(0);
            serverPlayer.getInventory().placeItemBackInInventory(original);
            CraftRoll.lost(serverPlayer, decision, Component.translatable("aurorion_profissoes.oficio.reforja_falhou"));
        } else if (decision == CraftRoll.Decision.CRIT) {
            CraftRoll.masterpiece(serverPlayer, stack);
        }
    }

    /** Lapidação: a gema lapidada sai do {@code assemble}, antes de o mod gastar as entradas e gravar a saída. */
    @ModifyExpressionValue(method = "clickMenuButton", require = 0, at = @At(value = "INVOKE",
            target = "Ldev/shadowsoffire/apotheosis/socket/gem/cutting/GemCuttingRecipe;assemble(Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack aurorion$cut(ItemStack gem, @Local(argsOnly = true) Player player) {
        if (!(player instanceof ServerPlayer serverPlayer) || gem.isEmpty()) return gem;
        CraftRoll.Decision decision = CraftRoll.forPlayer(serverPlayer, gem, 0, Profession.ARCANIST);
        if (decision.failed() || decision == CraftRoll.Decision.BLOCKED) {
            CraftRoll.failed(serverPlayer, decision == CraftRoll.Decision.BLOCKED ? CraftRoll.Decision.FAIL : decision);
            return ItemStack.EMPTY;
        }
        if (decision == CraftRoll.Decision.CRIT) CraftRoll.masterpiece(serverPlayer, gem);
        return gem;
    }

    /** Aumento: sigilos e XP já cobrados; na falha o afixo não é aplicado e o botão conta como usado. */
    @Inject(method = "clickMenuButton", require = 0, cancellable = true, at = @At(value = "INVOKE",
            target = "Ldev/shadowsoffire/apotheosis/affix/AffixHelper;applyAffix(Lnet/minecraft/world/item/ItemStack;Ldev/shadowsoffire/apotheosis/affix/AffixInstance;)V"))
    private void aurorion$augment(Player player, int id, CallbackInfoReturnable<Boolean> cir) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        ItemStack main = ((AbstractContainerMenu) (Object) this).getSlot(MAIN_SLOT).getItem();
        CraftRoll.Decision decision = CraftRoll.forPlayer(serverPlayer, main, 0, Profession.ARCANIST);
        if (!decision.failed() && decision != CraftRoll.Decision.BLOCKED) return;
        CraftRoll.lost(serverPlayer, decision, Component.translatable("aurorion_profissoes.oficio.aumento_falhou"));
        cir.setReturnValue(true);
    }
}
