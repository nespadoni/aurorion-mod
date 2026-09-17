package com.aurorion.profissoes.mixin;

import com.aurorion.profissoes.server.SpecialtyRules;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.EnchantmentScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.EnchantmentMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(EnchantmentScreen.class)
public abstract class EnchantmentScreenMixin extends AbstractContainerScreen<EnchantmentMenu> {
    protected EnchantmentScreenMixin(EnchantmentMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void aurorion$explainThirdOption(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (minecraft == null || minecraft.player == null || SpecialtyRules.canUseTableOption(minecraft.player, 2)
                || !menu.getSlot(0).hasItem() || !isHovering(60, 52, 108, 17, mouseX, mouseY)) return;
        graphics.renderComponentTooltip(font, List.of(
                Component.translatable("message.aurorion_profissoes.enchant.third_option_requires_arcanist")), mouseX, mouseY);
    }
}
