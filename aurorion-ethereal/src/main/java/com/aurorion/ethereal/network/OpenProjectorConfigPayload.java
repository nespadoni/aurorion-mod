package com.aurorion.ethereal.network;

import com.aurorion.ethereal.AurorionEthereal;
import com.aurorion.ethereal.ranking.BoardMode;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Servidor -> cliente: abre a tela do projetor com a configuracao atual dele. */
public record OpenProjectorConfigPayload(BlockPos pos, int mode, float holoWidth, float holoHeight)
        implements CustomPacketPayload {
    public static final Type<OpenProjectorConfigPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AurorionEthereal.MOD_ID, "open_projector_config"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenProjectorConfigPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, OpenProjectorConfigPayload::pos,
            ByteBufCodecs.VAR_INT, OpenProjectorConfigPayload::mode,
            ByteBufCodecs.FLOAT, OpenProjectorConfigPayload::holoWidth,
            ByteBufCodecs.FLOAT, OpenProjectorConfigPayload::holoHeight,
            OpenProjectorConfigPayload::new);

    public BoardMode boardMode() {
        return BoardMode.byOrdinal(mode);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
