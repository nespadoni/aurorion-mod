package com.aurorion.utils.mixin;

import com.aurorion.utils.render.SolidLayerCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * {@code ClientHooks.isBlockInSolidLayer} com a resposta guardada no estado ({@link SolidLayerCache}).
 *
 * <p>{@code @Overwrite}, e nao {@code @Inject}: o metodo roda nas threads de malha a cada face que
 * pergunta {@code supportsExternalFaceHiding()}, e um inject cancelavel alocaria um
 * {@code CallbackInfoReturnable} por chamada — exatamente o tipo de custo que isto tira. A conta e a do
 * NeoForge 21.1, linha por linha; so a primeira resposta de cada estado e calculada.
 */
@Mixin(value = ClientHooks.class, remap = false)
public abstract class ClientHooksSolidLayerMixin {
    /**
     * @author aurorion_utils
     * @reason Mesma resposta do NeoForge, calculada uma vez por estado em vez de uma vez por face.
     */
    @Overwrite
    public static boolean isBlockInSolidLayer(BlockState state) {
        SolidLayerCache cache = (SolidLayerCache) state;
        byte cached = cache.aurorion_utils$solidLayer();
        if (cached != 0) return cached == 1;
        BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
        boolean solid = model.getRenderTypes(state, RandomSource.create(), ModelData.EMPTY).contains(RenderType.solid());
        cache.aurorion_utils$setSolidLayer(solid ? (byte) 1 : (byte) 2);
        return solid;
    }
}
