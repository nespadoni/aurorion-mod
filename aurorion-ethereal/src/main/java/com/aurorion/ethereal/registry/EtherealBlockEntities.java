package com.aurorion.ethereal.registry;

import com.aurorion.ethereal.AurorionEthereal;
import com.aurorion.ethereal.block.entity.AeonicProjectorBlockEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class EtherealBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, AurorionEthereal.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AeonicProjectorBlockEntity>> AEONIC_PROJECTOR =
            BLOCK_ENTITIES.register("aeonic_projector", () -> BlockEntityType.Builder.of(
                    AeonicProjectorBlockEntity::new, EtherealBlocks.AEONIC_PROJECTOR.get()).build(null));

    private EtherealBlockEntities() {
    }
}
