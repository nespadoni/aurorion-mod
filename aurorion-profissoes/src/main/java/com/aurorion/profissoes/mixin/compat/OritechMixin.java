package com.aurorion.profissoes.mixin.compat;

import com.aurorion.profissoes.craft.CraftRoll;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;
import java.util.List;

/**
 * Oritech: toda máquina de receita (triturador, montadora, fundição, centrífuga, forja atômica, refinaria…)
 * passa por {@code MachineBlockEntity.craftItem} — as subclasses chamam o super. Um teste por receita, pela
 * saída principal, com a mão de quem colocou a máquina — Criação mais Tecnologia, como no Create.
 *
 * <p>Na falha todas as saídas viram vazias: o {@code craftItem} soma cada saída na pilha que já está no slot
 * sem comparar o item, então uma Peça Arruinada viraria produto a mais. Os insumos se gastam como sempre.
 */
@Pseudo
@Mixin(targets = "rearth.oritech.block.base.entity.MachineBlockEntity", remap = false)
public abstract class OritechMixin {
    @ModifyExpressionValue(method = "craftItem", require = 0, at = @At(value = "INVOKE",
            target = "Lrearth/oritech/block/base/entity/MachineBlockEntity;getCraftingResults(Lrearth/oritech/init/recipes/OritechRecipe;)Ljava/util/List;"))
    private List<ItemStack> aurorion$craftCheck(List<ItemStack> results) {
        ItemStack main = ItemStack.EMPTY;
        for (ItemStack stack : results) {
            if (!stack.isEmpty()) {
                main = stack;
                break;
            }
        }
        BlockEntity machine = (BlockEntity) (Object) this;
        if (!CraftRoll.forTechnology(machine, machine.getLevel() == null ? null : machine.getLevel().getRandom(), main).failed()) return results;
        CraftRoll.machineFailed(machine);
        List<ItemStack> nothing = new ArrayList<>(results.size());
        for (int i = 0; i < results.size(); i++) nothing.add(ItemStack.EMPTY);
        return nothing;
    }
}
