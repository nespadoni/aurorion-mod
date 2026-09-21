package com.aurorion.aeonita.registry;

import com.aurorion.aeonita.AurorionAeonita;
import com.aurorion.aeonita.item.UniformCapeItem;
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

    /**
     * As capas do uniforme da escola: uma preta para quem ainda nao tem casa, e uma por casa de
     * Ethereal.
     *
     * <p>A ordem das cinco casas segue o campo {@code order} delas no datapack do
     * {@code aurorion-ethereal}, para a aba do criativo sair na mesma ordem que o altar mostra; a
     * capa sem casa vem antes por ser a que se veste antes de escolher. E so ordem: este mod nao
     * le aquele datapack nem depende dele para nada (ver {@link UniformCapeItem}).
     *
     * <p>O nome do arquivo de textura e o proprio argumento, entao registrar uma capa nova e uma
     * linha aqui mais um PNG em {@code textures/entity/armor/uniform_cape/}.
     */
    public static final DeferredItem<UniformCapeItem> UNIFORM_CAPE_SEM_CASA = uniformCape("sem_casa");
    public static final DeferredItem<UniformCapeItem> UNIFORM_CAPE_VENTHRA = uniformCape("venthra");
    public static final DeferredItem<UniformCapeItem> UNIFORM_CAPE_SYLVARA = uniformCape("sylvara");
    public static final DeferredItem<UniformCapeItem> UNIFORM_CAPE_NYX = uniformCape("nyx");
    public static final DeferredItem<UniformCapeItem> UNIFORM_CAPE_IGNIVAR = uniformCape("ignivar");
    public static final DeferredItem<UniformCapeItem> UNIFORM_CAPE_AETHERIS = uniformCape("aetheris");

    private AeonitaItems() {
    }

    private static DeferredItem<UniformCapeItem> uniformCape(String house) {
        return ITEMS.register("uniform_cape_" + house, () -> new UniformCapeItem(house));
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
