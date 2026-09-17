package com.aurorion.ethereal.registry;

import com.aurorion.ethereal.AurorionEthereal;
import com.aurorion.ethereal.block.AeonicProjectorBlock;
import com.aurorion.ethereal.block.HouseMuralBlock;
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

    public static final DeferredBlock<HouseMuralBlock> HOUSE_MURAL = BLOCKS.registerBlock(
            "house_mural",
            HouseMuralBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(3.5F, 8.0F)
                    .sound(SoundType.STONE));

    private EtherealBlocks() {
    }
}
