package com.aurorion.aeonita.registry;

import com.aurorion.aeonita.AurorionAeonita;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/**
 * Tags publicadas por este mod. Sao o contrato com os outros mods do ecossistema e com datapacks:
 * comportamento fica em codigo, mas <em>quais</em> itens/blocos participam dele e dado (diretriz 4
 * do SDD) — da para incluir item de outro mod na luz dinamica sem recompilar nada.
 */
public final class AeonitaTags {
    /** Itens que acendem luz dinamica na mao, no chao ou num quadro (ver DynamicLightHandler). */
    public static final TagKey<Item> EMITS_LIGHT = itemTag("emits_light");

    private AeonitaTags() {
    }

    private static TagKey<Item> itemTag(String path) {
        return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(AurorionAeonita.MOD_ID, path));
    }
}
