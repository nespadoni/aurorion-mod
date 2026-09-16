package com.aurorion.profissoes.mixin.spoil;

import com.aurorion.profissoes.compat.FoodCompat;
import com.aurorion.profissoes.config.ProfessionsConfig;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.elcuruxa.foodspoil.recipe.FoodMergeRecipe", remap = false)
public abstract class FoodMergeRecipeMixin {
    @Inject(method = "matches(Lnet/minecraft/world/item/crafting/CraftingInput;Lnet/minecraft/world/level/Level;)Z", at = @At("HEAD"), cancellable = true)
    private void aurorion$preservePreparation(CraftingInput input, Level level, CallbackInfoReturnable<Boolean> cir) {
        if (!ProfessionsConfig.enabled()) return;
        // Esta receita reconstrói datas sem a taxa individual. O inventario ja junta lotes iguais com snapshots corretos.
        for (int i = 0; i < input.size(); i++) if (FoodCompat.grade(input.getItem(i)) >= 0) { cir.setReturnValue(false); return; }
    }
}
