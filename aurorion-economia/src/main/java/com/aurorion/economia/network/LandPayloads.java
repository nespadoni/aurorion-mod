package com.aurorion.economia.network;

import com.aurorion.economia.AurorionEconomia;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.List;
import java.util.UUID;

/** Fixed-size messages. The client never chooses the buyer, owner identity or payment destination. */
public final class LandPayloads {
    private LandPayloads() { }
    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(AurorionEconomia.MOD_ID, path);
    }
    public record Open(UUID token, String buyer, BlockPos origin, List<Long> rates, long floor, int maxSide)
            implements CustomPacketPayload {
        public static final Type<Open> TYPE = new Type<>(id("land_open"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Open> CODEC = StreamCodec.of((b, v) -> {
            b.writeUUID(v.token); b.writeUtf(v.buyer, 64); b.writeBlockPos(v.origin);
            for (long rate : v.rates) b.writeVarLong(rate);
            b.writeVarLong(v.floor); b.writeVarInt(v.maxSide);
        }, b -> new Open(b.readUUID(), b.readUtf(64), b.readBlockPos(),
                List.of(b.readVarLong(), b.readVarLong(), b.readVarLong(), b.readVarLong(), b.readVarLong()),
                b.readVarLong(), b.readVarInt()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record Submit(UUID token, int x, int z, int width, int length, int zone, String amount)
            implements CustomPacketPayload {
        public static final Type<Submit> TYPE = new Type<>(id("land_submit"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Submit> CODEC = StreamCodec.of((b, v) -> {
            b.writeUUID(v.token); b.writeInt(v.x); b.writeInt(v.z); b.writeVarInt(v.width);
            b.writeVarInt(v.length); b.writeVarInt(v.zone); b.writeUtf(v.amount, 32);
        }, b -> new Submit(b.readUUID(), b.readInt(), b.readInt(), b.readVarInt(), b.readVarInt(),
                b.readVarInt(), b.readUtf(32)));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record Approval(UUID token, String broker, String buyer, ResourceLocation dimension,
                           int x, int z, int width, int length, int zone, long price, long balance)
            implements CustomPacketPayload {
        public static final Type<Approval> TYPE = new Type<>(id("land_approval"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Approval> CODEC = StreamCodec.of((b, v) -> {
            b.writeUUID(v.token); b.writeUtf(v.broker, 64); b.writeUtf(v.buyer, 64);
            b.writeResourceLocation(v.dimension); b.writeInt(v.x); b.writeInt(v.z);
            b.writeVarInt(v.width); b.writeVarInt(v.length); b.writeVarInt(v.zone);
            b.writeVarLong(v.price); b.writeVarLong(v.balance);
        }, b -> new Approval(b.readUUID(), b.readUtf(64), b.readUtf(64), b.readResourceLocation(),
                b.readInt(), b.readInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarLong(), b.readVarLong()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record Respond(UUID token, boolean accept) implements CustomPacketPayload {
        public static final Type<Respond> TYPE = new Type<>(id("land_respond"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Respond> CODEC = StreamCodec.of((b, v) -> {
            b.writeUUID(v.token); b.writeBoolean(v.accept);
        }, b -> new Respond(b.readUUID(), b.readBoolean()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}
