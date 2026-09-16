package com.aurorion.profissoes.mixin;

import com.aurorion.profissoes.config.ProfessionsConfig;
import com.aurorion.profissoes.server.SpecialtyRules;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import java.util.function.Predicate;

/** A selecao vanilla de Mending le ENCHANTMENTS diretamente, sem o evento do NeoForge. */
@Mixin(EnchantmentHelper.class)
public abstract class EnchantmentRepairMixin {
    @ModifyVariable(method = "getRandomItemWith", at = @At("HEAD"), argsOnly = true)
    private static Predicate<ItemStack> aurorion$authorizedRepair(Predicate<ItemStack> filter,
            DataComponentType<?> effect, LivingEntity entity, Predicate<ItemStack> original) {
        return ProfessionsConfig.enabled() && effect == EnchantmentEffectComponents.REPAIR_WITH_XP
                ? stack -> SpecialtyRules.adminMending(stack) && filter.test(stack) : filter;
    }
}
