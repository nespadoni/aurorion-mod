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

import java.util.ArrayList;
import java.util.List;

/**
 * Create 6: todo processamento (prensa, misturador, mó, triturador, serra, ventilador, deployer) passa
 * por {@code ProcessingRecipe.rollResults}. Cada peça é testada com a ficha de quem montou a máquina
 * (Criação + Tecnologia; ver {@link MachineContext}), ou com o bônus de máquina se não há dono; a que falha
 * vira Peça Arruinada — material barato (CD 2) nunca falha; obra de CD alta, sim.
 */
@Pseudo
@Mixin(targets = "com.simibubi.create.content.processing.recipe.ProcessingRecipe", remap = false)
public abstract class ProcessingRecipeMixin {
    @Inject(method = "rollResults(Ljava/util/List;Lnet/minecraft/util/RandomSource;)Ljava/util/List;", at = @At("RETURN"), cancellable = true)
    private void aurorion$craftCheck(List<?> outputs, RandomSource random, CallbackInfoReturnable<List<ItemStack>> cir) {
        List<ItemStack> results = cir.getReturnValue();
        List<ItemStack> changed = null;
        for (int i = 0; i < results.size(); i++) {
            if (!CraftRoll.forTechnology(MachineContext.current(), random, results.get(i)).failed()) continue;
            if (changed == null) changed = new ArrayList<>(results);
            changed.set(i, CraftRoll.ruined());
        }
        if (changed != null) cir.setReturnValue(changed);
    }
}
