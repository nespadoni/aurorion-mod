package com.aurorion.profissoes.mixin.compat;

import com.aurorion.profissoes.craft.CraftRoll;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Estações de outros mods em que o jogador retira o resultado por um {@code onTake(Player, ItemStack)}
 * próprio, fora do {@code ResultSlot} e da mesa de ferraria do vanilla. A mesma regra da bancada
 * ({@link CraftRoll#take}): a peça já está na mão; na falha ela zera, os insumos se gastam pelo resto do
 * método do mod e sai a Peça Arruinada.
 *
 * <p>Alvos conferidos com javap nos jars do pack (ver OFICIO-ADAPTADORES.md). {@code @Pseudo}: um mod
 * ausente só deixa o alvo dele sem efeito. O shift-click dessas telas não passa por aqui quando o mod move
 * a peça antes do {@code onTake}; fica anotado no levantamento.
 */
@Pseudo
@Mixin(targets = {
        // Sophisticated Core: upgrade de crafting (herda ResultSlot, mas troca o onTake sem chamar super)
        "net.p3pp3rf1y.sophisticatedcore.upgrades.crafting.CraftingUpgradeContainer$2",
        // Iron's Spellbooks: forja de pergaminhos e bigorna arcana
        "io.redspace.ironsspellbooks.gui.scroll_forge.ScrollForgeMenu$4",
        "io.redspace.ironsspellbooks.gui.arcane_anvil.ArcaneAnvilMenu",
        // L_Ender's Cataclysm: bigorna de fusão mecânica
        "com.github.L_Ender.cataclysm.inventory.WeaponfusionMenu",
        // Legendary Survival Overhaul: mesa de costura
        "sfiomn.legendarysurvivaloverhaul.common.containers.SewingTableContainer",
        // Refurbished Furniture: bancada de construção
        "com.mrcrayfish.furniture.refurbished.inventory.WorkbenchMenu$WorkbenchResultSlot"
}, remap = false)
public abstract class ResultTakeMixin {
    @Inject(method = "onTake(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;)V", at = @At("HEAD"), require = 0)
    private void aurorion$craftCheck(Player player, ItemStack stack, CallbackInfo ci) {
        if (player instanceof ServerPlayer serverPlayer) CraftRoll.take(serverPlayer, stack);
    }
}
