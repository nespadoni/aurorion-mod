package com.aurorion.ethereal.network;

import com.aurorion.ethereal.AurorionEthereal;
import com.aurorion.ethereal.house.House;
import com.aurorion.ethereal.house.RiteStyle;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/** Uma fotografia da cena; nenhum vertice ou particula trafega por tick. */
public record RitePayload(UUID playerId, int entityId, boolean active, ResourceLocation dimension,
                          Vec3 anchor, int elapsed, Component houseName, Component motto,
                          int color, RiteStyle style) implements CustomPacketPayload {
    public static final Type<RitePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AurorionEthereal.MOD_ID, "binding_rite"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RitePayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeUUID(payload.playerId);
                buf.writeVarInt(payload.entityId);
                buf.writeBoolean(payload.active);
                ResourceLocation.STREAM_CODEC.encode(buf, payload.dimension);
                buf.writeDouble(payload.anchor.x);
                buf.writeDouble(payload.anchor.y);
                buf.writeDouble(payload.anchor.z);
                buf.writeVarInt(payload.elapsed);
                ComponentSerialization.STREAM_CODEC.encode(buf, payload.houseName);
                ComponentSerialization.STREAM_CODEC.encode(buf, payload.motto);
                buf.writeInt(payload.color);
                buf.writeInt(payload.style.secondary());
                buf.writeInt(payload.style.accent());
                ResourceLocation.STREAM_CODEC.encode(buf, payload.style.music());
            },
            buf -> new RitePayload(buf.readUUID(), buf.readVarInt(), buf.readBoolean(),
                    ResourceLocation.STREAM_CODEC.decode(buf),
                    new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()), buf.readVarInt(),
                    ComponentSerialization.STREAM_CODEC.decode(buf), ComponentSerialization.STREAM_CODEC.decode(buf),
                    buf.readInt(), new RiteStyle(buf.readInt(), buf.readInt(), ResourceLocation.STREAM_CODEC.decode(buf))));

    public static RitePayload start(ServerPlayer player, House house, Vec3 anchor, int elapsed) {
        return new RitePayload(player.getUUID(), player.getId(), true, player.level().dimension().location(),
                anchor, elapsed, house.name(), house.motto(), house.color(), house.ceremony());
    }

    public static RitePayload end(ServerPlayer player) {
        return new RitePayload(player.getUUID(), player.getId(), false, player.level().dimension().location(),
                Vec3.ZERO, 0, CommonComponents.EMPTY, CommonComponents.EMPTY, House.DEFAULT_COLOR, RiteStyle.DEFAULT);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
