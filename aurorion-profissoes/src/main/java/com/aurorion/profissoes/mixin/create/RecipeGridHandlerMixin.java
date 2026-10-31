package com.aurorion.profissoes.mixin.create;

import com.aurorion.profissoes.craft.CraftRoll;
import com.aurorion.profissoes.craft.compat.MachineContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Create 6, crafter mecânico: o resultado da grade sai de {@code RecipeGridHandler.tryToApplyRecipe}. Na
 * falha devolve a Peça Arruinada no lugar (não vazio: com resultado vazio o crafter não consome nada e
 * tentaria de novo).
 */
@Pseudo
@Mixin(targets = "com.simibubi.create.content.kinetics.crafter.RecipeGridHandler", remap = false)
public abstract class RecipeGridHandlerMixin {
    @Inject(method = "tryToApplyRecipe", at = @At("RETURN"), cancellable = true)
    private static void aurorion$craftCheck(Level level, @Coerce Object items, CallbackInfoReturnable<ItemStack> cir) {
        ItemStack result = cir.getReturnValue();
        if (result == null || result.isEmpty() || level.isClientSide()) return;
        if (CraftRoll.forTechnology(MachineContext.current(), level.getRandom(), result).failed()) cir.setReturnValue(CraftRoll.ruined());
    }
}
