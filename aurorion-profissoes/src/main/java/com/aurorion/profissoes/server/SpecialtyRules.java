package com.aurorion.profissoes.server;

import com.aurorion.profissoes.api.ProfessionApi;
import com.aurorion.profissoes.config.ProfessionsConfig;
import com.aurorion.profissoes.data.Profession;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.*;
import java.util.*;

public final class SpecialtyRules {
    private static final String ADMIN_MENDING = "AurorionAdminMending";
    private SpecialtyRules() {}
    public static boolean smith(Player player) {
        return !ProfessionsConfig.enabled() || ProfessionApi.staff(player) || ProfessionApi.has(player, Profession.SMITH);
    }
    public static boolean canUseTableOption(Player player, int option) {
        if (option < 0 || option >= 3) return false;
        return !ProfessionsConfig.enabled() || ProfessionApi.staff(player)
                || EnchantingTablePolicy.canUseOption(ProfessionApi.of(player), option);
    }
    public static boolean adminMending(ItemStack stack) {
        var data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.getUnsafe().getBoolean(ADMIN_MENDING);
    }
    public static void authorizeMending(ItemStack stack, boolean value) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            if (value) tag.putBoolean(ADMIN_MENDING, true); else tag.remove(ADMIN_MENDING);
        });
    }
    public static boolean legalAnvil(Player player, ItemStack original, ItemStack result) {
        if (!ProfessionsConfig.enabled() || ProfessionApi.staff(player)) return true;
        if (!smith(player)) return false;
        var before = EnchantmentHelper.getEnchantmentsForCrafting(original);
        for (var entry : EnchantmentHelper.getEnchantmentsForCrafting(result).entrySet()) {
            if (entry.getKey().is(Enchantments.MENDING)) {
                if (!adminMending(original) || entry.getIntValue() > before.getLevel(entry.getKey())) return false;
            } else if (entry.getIntValue() > ProfessionsConfig.COMMON_ENCHANT_MAX.get()
                    && entry.getIntValue() > before.getLevel(entry.getKey())) return false;
        }
        return true;
    }
    public static List<EnchantmentInstance> tableOptions(Player player, List<EnchantmentInstance> source) {
        if (!ProfessionsConfig.enabled() || ProfessionApi.staff(player)) return source;
        int limit = ProfessionApi.has(player, Profession.ARCANIST)
                ? ProfessionsConfig.SPECIALIST_ENCHANT_MAX.get() : ProfessionsConfig.COMMON_ENCHANT_MAX.get();
        var result = new ArrayList<EnchantmentInstance>(source.size());
        for (var entry : source) if (!entry.enchantment.is(Enchantments.MENDING))
            result.add(new EnchantmentInstance(entry.enchantment, Math.min(limit, entry.level)));
        return result;
    }
    public static void removeCommonMending(ItemStack stack) {
        if (!ProfessionsConfig.enabled() || adminMending(stack) || stack.isEmpty()) return;
        var before = EnchantmentHelper.getEnchantmentsForCrafting(stack);
        if (before.keySet().stream().noneMatch(e -> e.is(Enchantments.MENDING))) return;
        EnchantmentHelper.updateEnchantments(stack, enchants -> enchants.removeIf(e -> e.is(Enchantments.MENDING)));
    }
}
