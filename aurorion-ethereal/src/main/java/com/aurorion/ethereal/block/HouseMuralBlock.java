package com.aurorion.ethereal.block;

import com.aurorion.ethereal.block.entity.HouseMuralBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Bloco provisório do Mural; toda regra e todo estado compartilhado permanecem no servidor. */
public final class HouseMuralBlock extends BaseEntityBlock {
    public static final MapCodec<HouseMuralBlock> CODEC = simpleCodec(HouseMuralBlock::new);

    public HouseMuralBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HouseMuralBlockEntity(pos, state);
    }
}
