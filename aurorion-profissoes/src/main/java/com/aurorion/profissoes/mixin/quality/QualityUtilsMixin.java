package com.aurorion.profissoes.mixin.quality;

import com.aurorion.profissoes.compat.FoodCompat;
import com.aurorion.profissoes.compat.QualityNone;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Collection;

@Pseudo
@Mixin(targets = "de.cadentem.quality_food.util.QualityUtils", remap = false)
public abstract class QualityUtilsMixin {
    @Inject(method = "applyQuality(Lnet/minecraft/world/item/ItemStack;Ljava/util/Collection;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/core/RegistryAccess;)V", at = @At("RETURN"))
    private static void aurorion$production(ItemStack stack, Collection<ItemStack> ingredients, Player player, RegistryAccess access, CallbackInfo ci) {
        FoodCompat.produced(stack, player);
    }

    /**
     * Uma copia do {@code Quality.NONE} (item salvo ou sincronizado) volta como o proprio NONE. Sem isso
     * o Quality Food desenha o sprite inexistente {@code quality_food:none} no slot. Ver {@link QualityNone}.
     */
    @Inject(method = "getQuality(Lnet/minecraft/world/item/ItemStack;)Lde/cadentem/quality_food/core/codecs/Quality;",
            at = @At("RETURN"), cancellable = true)
    private static void aurorion$canonicalNone(ItemStack stack, CallbackInfoReturnable<Object> cir) {
        Object quality = cir.getReturnValue();
        Object canonical = QualityNone.canonical(quality);
        if (canonical != quality) cir.setReturnValue(canonical);
    }
}
