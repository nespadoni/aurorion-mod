package com.aurorion.profissoes.loot;

import com.aurorion.profissoes.AurorionProfissoes;
import com.aurorion.profissoes.server.SpecialtyRules;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.*;
import net.neoforged.neoforge.registries.*;

public final class NoMendingLoot extends LootModifier {
    public static final MapCodec<NoMendingLoot> CODEC = RecordCodecBuilder.mapCodec(instance -> codecStart(instance).apply(instance, NoMendingLoot::new));
    public static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> REGISTRY = DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, AurorionProfissoes.MOD_ID);
    static { REGISTRY.register("no_mending", () -> CODEC); }
    public NoMendingLoot(LootItemCondition[] conditions) { super(conditions); }
    @Override protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext context) {
        for (int i = 0; i < loot.size(); i++) {
            var stack = loot.get(i);
            SpecialtyRules.removeCommonMending(stack);
            if (stack.is(Items.ENCHANTED_BOOK) && EnchantmentHelper.getEnchantmentsForCrafting(stack).isEmpty())
                loot.set(i, stack.transmuteCopy(Items.BOOK));
        }
        return loot;
    }
    @Override public MapCodec<? extends IGlobalLootModifier> codec() { return CODEC; }
}
