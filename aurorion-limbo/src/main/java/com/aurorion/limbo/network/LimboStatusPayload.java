package com.aurorion.limbo.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Snapshot do dono da tela. -1 desliga; nunca revela outros exilados. */
public record LimboStatusPayload(long remainingMillis, int stage, BlockPos door, int facing) implements CustomPacketPayload {
    public static final LimboStatusPayload CLEAR = new LimboStatusPayload(-1, 0, BlockPos.ZERO, 0);
    public static final Type<LimboStatusPayload> TYPE = new Type<>(ResourceLocation.parse("aurorion_limbo:status"));
    public static final StreamCodec<RegistryFriendlyByteBuf, LimboStatusPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, LimboStatusPayload::remainingMillis,
            ByteBufCodecs.VAR_INT, LimboStatusPayload::stage,
            BlockPos.STREAM_CODEC, LimboStatusPayload::door,
            ByteBufCodecs.VAR_INT, LimboStatusPayload::facing, LimboStatusPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
