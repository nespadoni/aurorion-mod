package com.aurorion.aeonita.registry;

import com.aurorion.aeonita.AurorionAeonita;
import com.aurorion.aeonita.block.SelectionAltarBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class AeonitaBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(AurorionAeonita.MOD_ID);

    /** Blocos de armazenamento: 9 lingotes viram 1 bloco, e o bloco acende de verdade (nao e a luz falsa do cliente). */
    public static final DeferredBlock<Block> AEONITA_BLOCK_YELLOW = BLOCKS.registerSimpleBlock("aeonita_block_yellow", storageBlock(MapColor.GOLD));
    public static final DeferredBlock<Block> AEONITA_BLOCK_BLUE = BLOCKS.registerSimpleBlock("aeonita_block_blue", storageBlock(MapColor.COLOR_BLUE));
    public static final DeferredBlock<Block> AEONITA_BLOCK_RED = BLOCKS.registerSimpleBlock("aeonita_block_red", storageBlock(MapColor.COLOR_RED));

    /**
     * Altar de Selecao. Aqui ele e so um bloco decorativo com forma propria — o clique nele nao faz
     * nada neste mod. Quem escuta o clique e da significado ritual e o mod do ato ativo, casando
     * pela tag de bloco em vez de pela classe, entao este mod nao precisa saber que ato existe.
     */
    public static final DeferredBlock<SelectionAltarBlock> SELECTION_ALTAR = BLOCKS.registerBlock(
            "selection_altar",
            SelectionAltarBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.DEEPSLATE)
                    .strength(3.0f, 9.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.DEEPSLATE)
                    .lightLevel(state -> 7)
                    .noOcclusion());

    private AeonitaBlocks() {
    }

    private static BlockBehaviour.Properties storageBlock(MapColor color) {
        return BlockBehaviour.Properties.of()
                .mapColor(color)
                .strength(3.0f, 6.0f)
                .requiresCorrectToolForDrops()
                .sound(SoundType.AMETHYST)
                .lightLevel(state -> 10);
    }
}
