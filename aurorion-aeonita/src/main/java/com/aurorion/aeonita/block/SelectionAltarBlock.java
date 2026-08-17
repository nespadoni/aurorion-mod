package com.aurorion.aeonita.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Pedestal de tres degraus. Nao tem estado, nao tem block entity e nao tem tick — o unico motivo de
 * existir uma classe em vez de {@code registerSimpleBlock} e a forma de colisao, que e constante e
 * fica montada uma vez so num {@code static final}.
 */
public class SelectionAltarBlock extends Block {
    private static final VoxelShape SHAPE = Shapes.or(
            box(0.0, 0.0, 0.0, 16.0, 4.0, 16.0),
            box(3.0, 4.0, 3.0, 13.0, 12.0, 13.0),
            box(1.0, 12.0, 1.0, 15.0, 16.0, 15.0));

    public SelectionAltarBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
}
