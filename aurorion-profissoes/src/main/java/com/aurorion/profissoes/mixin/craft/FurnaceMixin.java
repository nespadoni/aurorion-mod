package com.aurorion.profissoes.mixin.craft;

import com.aurorion.profissoes.config.ProfessionsConfig;
import com.aurorion.profissoes.craft.CraftRoll;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fornalha, defumador, alto-forno e toda fornalha de mod que herda de {@link AbstractFurnaceBlockEntity}:
 * cada peça que termina passa pelo teste de ofício com a mão de quem colocou o bloco (o retrato da ficha
 * fica em disco, então vale offline), ou com o bônus de máquina se não há dono. Na falha a entrada se
 * perde e nada sai — nada de item estranho travando a saída da fornalha.
 *
 * <p>O {@code burn} só é chamado depois do {@code canBurn}, uma vez por peça; o custo é uma leitura da
 * CD e um número aleatório a cada peça fundida, não por tick.
 */
@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class FurnaceMixin {
    @Inject(method = "burn", at = @At("HEAD"), cancellable = true)
    private static void aurorion$craftCheck(RegistryAccess registries, @Nullable RecipeHolder<?> recipe, NonNullList<ItemStack> items,
                                            int maxStackSize, AbstractFurnaceBlockEntity furnace, CallbackInfoReturnable<Boolean> cir) {
        if (recipe == null || items.get(0).isEmpty() || furnace.getLevel() == null || furnace.getLevel().isClientSide()) return;
        ItemStack result = recipe.value().getResultItem(registries);
        if (!CraftRoll.forBlock(furnace, result, ProfessionsConfig.FURNACE_EXTRA_DC.get()).failed()) return;
        items.get(0).shrink(1);
        CraftRoll.machineFailed(furnace);
        cir.setReturnValue(false);
    }
}
