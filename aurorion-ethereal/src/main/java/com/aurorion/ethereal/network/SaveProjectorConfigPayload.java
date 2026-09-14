package com.aurorion.ethereal.network;

import com.aurorion.ethereal.AurorionEthereal;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Cliente -> servidor: o que a tela do projetor configurou.
 *
 * <p>Tamanho e meta sao reaplicados do outro lado (permissao, distancia e limites): a tela nunca e
 * autoridade sobre nada.
 */
public record SaveProjectorConfigPayload(BlockPos pos, int mode, float holoWidth, float holoHeight)
        implements CustomPacketPayload {
    public static final Type<SaveProjectorConfigPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AurorionEthereal.MOD_ID, "save_projector_config"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SaveProjectorConfigPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, SaveProjectorConfigPayload::pos,
            ByteBufCodecs.VAR_INT, SaveProjectorConfigPayload::mode,
            ByteBufCodecs.FLOAT, SaveProjectorConfigPayload::holoWidth,
            ByteBufCodecs.FLOAT, SaveProjectorConfigPayload::holoHeight,
            SaveProjectorConfigPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
