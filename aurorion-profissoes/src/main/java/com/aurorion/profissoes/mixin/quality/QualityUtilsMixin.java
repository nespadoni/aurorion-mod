package com.aurorion.profissoes.mixin.quality;

import com.aurorion.profissoes.compat.FoodCompat;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.Collection;

@Pseudo
@Mixin(targets = "de.cadentem.quality_food.util.QualityUtils", remap = false)
public abstract class QualityUtilsMixin {
    @Inject(method = "applyQuality(Lnet/minecraft/world/item/ItemStack;Ljava/util/Collection;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/core/RegistryAccess;)V", at = @At("RETURN"))
    private static void aurorion$production(ItemStack stack, Collection<ItemStack> ingredients, Player player, RegistryAccess access, CallbackInfo ci) {
        FoodCompat.produced(stack, player);
    }
}
