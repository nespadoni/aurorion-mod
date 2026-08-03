package com.aurorion.talk.network;

import com.aurorion.talk.AurorionTalk;
import com.aurorion.talk.style.BalloonStyle;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Snapshot completo dos estilos, enviado uma unica vez quando o jogador entra.
 * Inclui so quem esta online no momento — com 80 jogadores isso mantem o pacote pequeno mesmo que
 * o mundo tenha historico de milhares de entradas.
 */
public record SyncStylesPayload(Map<UUID, BalloonStyle> styles) implements CustomPacketPayload {
    public static final Type<SyncStylesPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AurorionTalk.MOD_ID, "sync_styles"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncStylesPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.map(HashMap::new, UUIDUtil.STREAM_CODEC, BalloonStyle.STREAM_CODEC),
            SyncStylesPayload::styles,
            SyncStylesPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
