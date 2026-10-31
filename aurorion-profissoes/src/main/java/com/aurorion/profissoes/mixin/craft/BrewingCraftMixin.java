package com.aurorion.profissoes.mixin.craft;

import com.aurorion.profissoes.craft.CraftRoll;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Suporte de poções: cada frasco que a destilação mudou passa pelo teste de ofício com a mão de quem colocou
 * o suporte (o médico rola com vantagem). O que falha desanda numa poção mundana do mesmo tipo de frasco
 * (comum, arremessável, persistente) — nada de item estranho preso no suporte.
 *
 * <p>O {@code doBrew} roda uma vez por destilação; o retrato de antes é guardado no começo e comparado no
 * fim, para um frasco que não reagiu ao ingrediente não ser testado.
 */
@Mixin(BrewingStandBlockEntity.class)
public abstract class BrewingCraftMixin {
    private static final int BOTTLES = 3;
    /** Os frascos antes da destilação. Thread do servidor; {@code doBrew} não é reentrante. */
    @Unique
    private static final ItemStack[] aurorion$before = new ItemStack[BOTTLES];

    @Inject(method = "doBrew", at = @At("HEAD"))
    private static void aurorion$snapshot(Level level, BlockPos pos, NonNullList<ItemStack> items, CallbackInfo ci) {
        for (int i = 0; i < BOTTLES; i++) aurorion$before[i] = items.get(i);
    }

    @Inject(method = "doBrew", at = @At("RETURN"))
    private static void aurorion$craftCheck(Level level, BlockPos pos, NonNullList<ItemStack> items, CallbackInfo ci) {
        BlockEntity stand = level.isClientSide() ? null : level.getBlockEntity(pos);
        for (int i = 0; i < BOTTLES; i++) {
            ItemStack before = aurorion$before[i];
            aurorion$before[i] = null;
            ItemStack after = items.get(i);
            if (stand == null || after.isEmpty() || before != null && ItemStack.matches(before, after)) continue;
            if (!after.has(DataComponents.POTION_CONTENTS) || !CraftRoll.forBlock(stand, after, 0).failed()) continue;
            items.set(i, PotionContents.createItemStack(after.getItem(), Potions.MUNDANE));
            CraftRoll.machineFailed(stand);
        }
    }
}
