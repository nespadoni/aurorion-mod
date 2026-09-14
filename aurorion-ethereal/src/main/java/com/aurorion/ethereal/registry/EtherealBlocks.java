package com.aurorion.ethereal.registry;

import com.aurorion.ethereal.AurorionEthereal;
import com.aurorion.ethereal.block.AeonicProjectorBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class EtherealBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(AurorionEthereal.MOD_ID);

    /** As mesmas propriedades do mod de referencia: ametista, luz 8, sem oclusao. */
    public static final DeferredBlock<AeonicProjectorBlock> AEONIC_PROJECTOR = BLOCKS.registerBlock(
            "aeonic_projector",
            AeonicProjectorBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .strength(3.0F, 6.0F)
                    .sound(SoundType.AMETHYST)
                    .lightLevel(state -> 8)
                    .noOcclusion());

    private EtherealBlocks() {
    }
}
