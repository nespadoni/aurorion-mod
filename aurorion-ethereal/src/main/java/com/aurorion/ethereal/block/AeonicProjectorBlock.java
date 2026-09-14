package com.aurorion.ethereal.block;

import com.aurorion.ethereal.block.entity.AeonicProjectorBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A base do Projetor Aeonico. O holograma em si e desenhado pelo renderizador do block entity.
 *
 * <p><b>Sem propriedade de direcao, de proposito.</b> O painel nao aponta para onde o bloco foi
 * colocado: ele gira para encarar quem esta olhando, no renderizador. Um projetor com FACING daria a
 * quem constroi a impressao de que a orientacao importa, e ela nao importa.
 */
public final class AeonicProjectorBlock extends BaseEntityBlock {
    public static final MapCodec<AeonicProjectorBlock> CODEC = simpleCodec(AeonicProjectorBlock::new);

    /** O contorno do corpo do projetor, nao do holograma — o painel nao tem colisao nem alvo. */
    private static final VoxelShape SHAPE = box(2.0, 0.0, 2.0, 14.0, 15.0, 14.0);

    public AeonicProjectorBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    /**
     * MODEL, e nao ENTITYBLOCK_ANIMATED como no mod de referencia: o corpo do projetor e o modelo
     * JSON do vanilla (a mesma geometria exportada do Blockbench), entao o chunk o desenha junto com
     * o resto do mundo, em batch. So o holograma passa pelo renderizador por frame.
     */
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AeonicProjectorBlockEntity(pos, state);
    }
}
