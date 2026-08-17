package com.aurorion.aeonita.registry;

import com.aurorion.aeonita.AurorionAeonita;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class AeonitaItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(AurorionAeonita.MOD_ID);

    public static final DeferredItem<Item> AEONITA_INGOT_YELLOW = ITEMS.registerSimpleItem("aeonita_ingot_yellow", ingot());
    public static final DeferredItem<Item> AEONITA_INGOT_BLUE = ITEMS.registerSimpleItem("aeonita_ingot_blue", ingot());
    public static final DeferredItem<Item> AEONITA_INGOT_RED = ITEMS.registerSimpleItem("aeonita_ingot_red", ingot());

    public static final DeferredItem<BlockItem> AEONITA_BLOCK_YELLOW = ITEMS.registerSimpleBlockItem(AeonitaBlocks.AEONITA_BLOCK_YELLOW);
    public static final DeferredItem<BlockItem> AEONITA_BLOCK_BLUE = ITEMS.registerSimpleBlockItem(AeonitaBlocks.AEONITA_BLOCK_BLUE);
    public static final DeferredItem<BlockItem> AEONITA_BLOCK_RED = ITEMS.registerSimpleBlockItem(AeonitaBlocks.AEONITA_BLOCK_RED);

    public static final DeferredItem<BlockItem> SELECTION_ALTAR = ITEMS.registerSimpleBlockItem(AeonitaBlocks.SELECTION_ALTAR);

    private AeonitaItems() {
    }

    /** Lingote de Aeonita: comestivel em qualquer situacao e sempre da Brilho ao comer. */
    private static Item.Properties ingot() {
        FoodProperties food = new FoodProperties.Builder()
                .alwaysEdible()
                .nutrition(4)
                .saturationModifier(0.8f)
                .effect(() -> new MobEffectInstance(MobEffects.GLOWING, 200, 0), 1.0f)
                .build();

        return new Item.Properties().food(food).rarity(Rarity.UNCOMMON);
    }
}
