package com.aurorion.areas.mixin.lso;

import com.aurorion.areas.compat.LsoThirstCompat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Promove a agua recem-posta no cantil a purificada quando a area concede — ver
 * {@link LsoThirstCompat} para o porque de o gancho ser o {@code fill} e nao o clique.
 *
 * <p>Alcanca o cantil grande de graca: {@code LargeCanteenItem estende CanteenItem} e nao sobrescreve
 * {@code fill} (conferido no jar 2.4.7.2).
 *
 * <p>{@code @At("RETURN")}: o LSO grava {@code NORMAL} no fim do proprio {@code fill}, entao escrever
 * antes dele seria escrever para ser apagado em seguida.
 */
@Pseudo
@Mixin(targets = "sfiomn.legendarysurvivaloverhaul.common.items.drink.CanteenItem", remap = false)
public abstract class CanteenFillMixin {

    @Inject(method = "fill(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;)V",
            at = @At("RETURN"))
    private void aurorion$purifyInsideGrantingArea(ItemStack stack, Level level, CallbackInfo ci) {
        LsoThirstCompat.afterFill(stack, level);
    }
}
