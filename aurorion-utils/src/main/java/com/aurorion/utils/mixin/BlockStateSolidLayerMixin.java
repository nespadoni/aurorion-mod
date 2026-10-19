package com.aurorion.utils.mixin;

import com.aurorion.utils.render.SolidLayerCache;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Um byte por estado de bloco, so no cliente: o cache de {@link SolidLayerCache}. */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateSolidLayerMixin implements SolidLayerCache {
    @Unique
    private byte aurorion_utils$solidLayer;

    @Override
    public byte aurorion_utils$solidLayer() {
        return aurorion_utils$solidLayer;
    }

    @Override
    public void aurorion_utils$setSolidLayer(byte value) {
        aurorion_utils$solidLayer = value;
    }
}
