package com.aurorion.ethereal.network;

import com.aurorion.ethereal.AurorionEthereal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.UUID;

/** Payloads limitados e orientados a evento usados pelo Mural da Casa. */
public final class HouseMuralPayloads {
    private HouseMuralPayloads() { }

    public record Open(
            BlockPos pos, Component houseName, int houseColor,
            boolean economyAvailable, long balance, long capacity, int vaultLevel,
            int protectorLevel, long remainingMillis, boolean livesAvailable
    ) implements CustomPacketPayload {
        public static final Type<Open> TYPE = new Type<>(id("open_house_mural"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Open> STREAM_CODEC =
                StreamCodec.of(Open::write, Open::read);

        private static void write(RegistryFriendlyByteBuf buffer, Open value) {
            BlockPos.STREAM_CODEC.encode(buffer, value.pos);
            ComponentSerialization.STREAM_CODEC.encode(buffer, value.houseName);
            buffer.writeInt(value.houseColor);
            buffer.writeBoolean(value.economyAvailable);
            buffer.writeVarLong(value.balance);
            buffer.writeVarLong(value.capacity);
            buffer.writeVarInt(value.vaultLevel);
            buffer.writeVarInt(value.protectorLevel);
            buffer.writeVarLong(value.remainingMillis);
            buffer.writeBoolean(value.livesAvailable);
        }

        private static Open read(RegistryFriendlyByteBuf buffer) {
            return new Open(BlockPos.STREAM_CODEC.decode(buffer),
                    ComponentSerialization.STREAM_CODEC.decode(buffer), buffer.readInt(),
                    buffer.readBoolean(), buffer.readVarLong(), buffer.readVarLong(),
                    buffer.readVarInt(), buffer.readVarInt(), buffer.readVarLong(), buffer.readBoolean());
        }

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Target(UUID id, String name, int lives, int maxLives, boolean online) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Target> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, Target::id,
                ByteBufCodecs.stringUtf8(64), Target::name,
                ByteBufCodecs.VAR_INT, Target::lives,
                ByteBufCodecs.VAR_INT, Target::maxLives,
                ByteBufCodecs.BOOL, Target::online,
                Target::new);
    }

    public record OpenTargets(BlockPos pos, Component houseName, List<Target> targets)
            implements CustomPacketPayload {
        public static final int MAX_TARGETS = 128;
        public static final Type<OpenTargets> TYPE = new Type<>(id("open_protector_targets"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenTargets> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, OpenTargets::pos,
                ComponentSerialization.STREAM_CODEC, OpenTargets::houseName,
                Target.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_TARGETS)), OpenTargets::targets,
                OpenTargets::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record OpenProtector(BlockPos pos) implements CustomPacketPayload {
        public static final Type<OpenProtector> TYPE = new Type<>(id("open_protector"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenProtector> STREAM_CODEC =
                StreamCodec.composite(BlockPos.STREAM_CODEC, OpenProtector::pos, OpenProtector::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record GrantLife(BlockPos pos, UUID target) implements CustomPacketPayload {
        public static final Type<GrantLife> TYPE = new Type<>(id("grant_house_life"));
        public static final StreamCodec<RegistryFriendlyByteBuf, GrantLife> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, GrantLife::pos,
                UUIDUtil.STREAM_CODEC, GrantLife::target,
                GrantLife::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(AurorionEthereal.MOD_ID, path);
    }
}
