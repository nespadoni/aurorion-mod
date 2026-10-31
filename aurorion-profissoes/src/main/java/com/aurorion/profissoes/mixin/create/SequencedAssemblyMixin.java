package com.aurorion.profissoes.mixin.create;

import com.aurorion.profissoes.craft.CraftRoll;
import com.aurorion.profissoes.craft.compat.MachineContext;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Create 6, montagem sequencial: o item final sai do {@code rollResult} privado, chamado só no último
 * passo do {@code advance}. Os passos intermediários já passam pelo {@code rollResults} das máquinas
 * (com o item de transição, de CD baixa); aqui é a obra pronta.
 */
@Pseudo
@Mixin(targets = "com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe", remap = false)
public abstract class SequencedAssemblyMixin {
    @Inject(method = "rollResult(Lnet/minecraft/util/RandomSource;)Lnet/minecraft/world/item/ItemStack;", at = @At("RETURN"), cancellable = true)
    private void aurorion$craftCheck(RandomSource random, CallbackInfoReturnable<ItemStack> cir) {
        ItemStack result = cir.getReturnValue();
        if (result != null && !result.isEmpty() && CraftRoll.forTechnology(MachineContext.current(), random, result).failed()) cir.setReturnValue(CraftRoll.ruined());
    }
}
