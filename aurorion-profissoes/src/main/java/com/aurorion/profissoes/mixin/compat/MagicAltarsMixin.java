package com.aurorion.profissoes.mixin.compat;

import com.aurorion.profissoes.craft.CraftRoll;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Altares: a infusão de espíritos do Malum e os altares de infusão e despertar do Mystical Agriculture. O
 * resultado nasce quando o ritual termina e vai para o chão (Malum) ou para o lugar do item central do
 * altar (Mystical Agriculture) — nos dois casos a Peça Arruinada pode sair no lugar sem travar nada. A mão é
 * de quem colocou o altar.
 */
@Pseudo
@Mixin(targets = {
        "com.sammy.malum.common.block.curiosities.spirit_altar.SpiritAltarBlockEntity",
        "com.blakebr0.mysticalagriculture.tileentity.InfusionAltarTileEntity",
        "com.blakebr0.mysticalagriculture.tileentity.AwakeningAltarTileEntity"
}, remap = false)
public abstract class MagicAltarsMixin {
    /** Malum: {@code craft(ServerLevel)} roda uma vez, no fim da infusão. */
    @ModifyExpressionValue(method = "craft", require = 0, at = @At(value = "INVOKE",
            target = "Lcom/sammy/malum/common/recipe/SpiritInfusionRecipe;getOutput(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack aurorion$malum(ItemStack result) {
        BlockEntity altar = (BlockEntity) (Object) this;
        if (!CraftRoll.forBlock(altar, result, 0).failed()) return result;
        CraftRoll.machineFailed(altar);
        return CraftRoll.ruined();
    }

    /** Mystical Agriculture, infusão: o {@code assemble} só roda quando o ritual completa ({@code tick} estático). */
    @ModifyExpressionValue(method = "tick", require = 0, at = @At(value = "INVOKE",
            target = "Lcom/blakebr0/mysticalagriculture/api/crafting/IInfusionRecipe;assemble(Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/world/item/ItemStack;"))
    private static ItemStack aurorion$infusion(ItemStack result, @Local(argsOnly = true) Level level, @Local(argsOnly = true) BlockPos pos) {
        return mystical(result, level, pos);
    }

    /** Mystical Agriculture, despertar. */
    @ModifyExpressionValue(method = "tick", require = 0, at = @At(value = "INVOKE",
            target = "Lcom/blakebr0/mysticalagriculture/api/crafting/IAwakeningRecipe;assemble(Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/world/item/ItemStack;"))
    private static ItemStack aurorion$awakening(ItemStack result, @Local(argsOnly = true) Level level, @Local(argsOnly = true) BlockPos pos) {
        return mystical(result, level, pos);
    }

    private static ItemStack mystical(ItemStack result, Level level, BlockPos pos) {
        BlockEntity altar = level.isClientSide() ? null : level.getBlockEntity(pos);
        if (altar == null || !CraftRoll.forBlock(altar, result, 0).failed()) return result;
        CraftRoll.machineFailed(altar);
        return CraftRoll.ruined();
    }
}
