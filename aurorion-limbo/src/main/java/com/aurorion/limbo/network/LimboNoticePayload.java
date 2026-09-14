package com.aurorion.limbo.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Uma mensagem por acontecimento; textos continuam traduziveis por resource pack. */
public record LimboNoticePayload(int kind, Component body) implements CustomPacketPayload {
    public static final int FALL = 0, ARRIVAL = 1, DEADLINE = 2, LEASH = 3, WINDOW = 4,
            DOOR = 5, ESCAPED = 6, RESCUED = 7, EXPIRED = 8, PUBLIC = 9;
    public static final Type<LimboNoticePayload> TYPE = new Type<>(ResourceLocation.parse("aurorion_limbo:notice"));
    public static final StreamCodec<RegistryFriendlyByteBuf, LimboNoticePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, LimboNoticePayload::kind,
            ComponentSerialization.STREAM_CODEC, LimboNoticePayload::body, LimboNoticePayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
