package com.aurorion.ethereal.registry;

import com.aurorion.ethereal.AurorionEthereal;
import net.minecraft.world.item.BlockItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class EtherealItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(AurorionEthereal.MOD_ID);

    public static final DeferredItem<BlockItem> AEONIC_PROJECTOR =
            ITEMS.registerSimpleBlockItem(EtherealBlocks.AEONIC_PROJECTOR);

    public static final DeferredItem<BlockItem> HOUSE_MURAL =
            ITEMS.registerSimpleBlockItem(EtherealBlocks.HOUSE_MURAL);

    private EtherealItems() {
    }
}
