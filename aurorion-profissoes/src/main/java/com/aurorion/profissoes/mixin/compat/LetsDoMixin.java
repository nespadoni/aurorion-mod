package com.aurorion.profissoes.mixin.compat;

import com.aurorion.profissoes.craft.CraftRoll;
import com.aurorion.profissoes.craft.compat.OwnerField;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Let's Do (Farm &amp; Charm, Candlelight): panela, torrador, fogão, frigideira e caldeirão grande. A saída
 * sai de {@code generateOutputItem}, que também é chamado no {@code canCraft} — por isso o teste fica na
 * chamada de dentro do {@code craft}, que só roda quando a receita termina. A mão é a de quem abriu o menu
 * por último ({@code ownerUuid} do próprio mod), senão a de quem colocou o bloco.
 *
 * <p>Na falha sai vazio: o {@code craft} soma a saída na pilha que já está no slot sem comparar o item, então
 * uma Peça Arruinada ali viraria uma refeição a mais.
 */
@Pseudo
@Mixin(targets = {
        "net.satisfy.farm_and_charm.core.block.entity.CookingPotBlockEntity",
        "net.satisfy.farm_and_charm.core.block.entity.RoasterBlockEntity",
        "net.satisfy.farm_and_charm.core.block.entity.StoveBlockEntity",
        "net.satisfy.candlelight.core.block.entity.CookingPanBlockEntity",
        "net.satisfy.candlelight.core.block.entity.LargeCookingPotBlockEntity"
}, remap = false)
public abstract class LetsDoMixin {
    private static final String RESULT = "(Lnet/minecraft/world/item/crafting/Recipe;Lnet/minecraft/core/RegistryAccess;)Lnet/minecraft/world/item/ItemStack;";

    @ModifyExpressionValue(method = "craft", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/satisfy/farm_and_charm/core/block/entity/CookingPotBlockEntity;generateOutputItem" + RESULT))
    private ItemStack aurorion$pot(ItemStack result) {
        return check(result);
    }

    @ModifyExpressionValue(method = "craft", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/satisfy/farm_and_charm/core/block/entity/RoasterBlockEntity;generateOutputItem" + RESULT))
    private ItemStack aurorion$roaster(ItemStack result) {
        return check(result);
    }

    @ModifyExpressionValue(method = "craft", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/satisfy/farm_and_charm/core/block/entity/StoveBlockEntity;generateOutputItem(Lnet/satisfy/farm_and_charm/core/recipe/StoveRecipe;Lnet/minecraft/core/RegistryAccess;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack aurorion$stove(ItemStack result) {
        return check(result);
    }

    @ModifyExpressionValue(method = "craft", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/satisfy/candlelight/core/block/entity/CookingPanBlockEntity;generateOutputItem" + RESULT))
    private ItemStack aurorion$pan(ItemStack result) {
        return check(result);
    }

    @ModifyExpressionValue(method = "craft", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/satisfy/candlelight/core/block/entity/LargeCookingPotBlockEntity;generateOutputItem" + RESULT))
    private ItemStack aurorion$largePot(ItemStack result) {
        return check(result);
    }

    private ItemStack check(ItemStack result) {
        BlockEntity block = (BlockEntity) (Object) this;
        if (!CraftRoll.forBlock(block, result, 0, OwnerField.of(block)).failed()) return result;
        CraftRoll.machineFailed(block);
        return ItemStack.EMPTY;
    }
}
