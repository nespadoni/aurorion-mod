package com.aurorion.talk.network;

import com.aurorion.talk.AurorionTalk;
import com.aurorion.talk.style.BalloonStyle;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;
import java.util.UUID;

/**
 * Broadcast do servidor: um unico jogador mudou de estilo (ou voltou ao padrao).
 *
 * @param style {@link Optional#empty()} significa "voltou ao estilo padrao".
 */
public record UpdateStylePayload(UUID player, Optional<BalloonStyle> style) implements CustomPacketPayload {
    public static final Type<UpdateStylePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AurorionTalk.MOD_ID, "update_style"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UpdateStylePayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, UpdateStylePayload::player,
            ByteBufCodecs.optional(BalloonStyle.STREAM_CODEC), UpdateStylePayload::style,
            UpdateStylePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
