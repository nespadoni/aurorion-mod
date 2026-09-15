package com.aurorion.areas.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Constant-size, S2C only. No region geometry or other characters' positions cross the network. */
public record AreaStatePayload(ResourceLocation dimension, float fogDistance, int fogColor,
                               float vignette, int pulseTicks, boolean flightBlocked) implements CustomPacketPayload {
    public static final Type<AreaStatePayload> TYPE = new Type<>(ResourceLocation.parse("aurorion_areas:state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AreaStatePayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeResourceLocation(p.dimension); buf.writeFloat(p.fogDistance); buf.writeInt(p.fogColor);
                buf.writeFloat(p.vignette); buf.writeVarInt(p.pulseTicks); buf.writeBoolean(p.flightBlocked);
            }, buf -> new AreaStatePayload(buf.readResourceLocation(), buf.readFloat(), buf.readInt(),
                    buf.readFloat(), buf.readVarInt(), buf.readBoolean()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
