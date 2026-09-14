package com.aurorion.limbo.network;

import com.aurorion.limbo.finale.FinaleScript;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Only a server-confirmed death or explicit staff preview starts the picture and music. */
public record FinalePayload(boolean preview, long elapsedMillis, FinaleScript script) implements CustomPacketPayload {
    public FinalePayload { elapsedMillis = Math.clamp(elapsedMillis, 0, script.totalMillis()); }
    public static final Type<FinalePayload> TYPE = new Type<>(ResourceLocation.parse("aurorion_limbo:finale"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FinalePayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeBoolean(p.preview); buf.writeLong(p.elapsedMillis); p.script.write(buf);
            }, buf -> new FinalePayload(buf.readBoolean(), buf.readLong(), FinaleScript.read(buf)));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
