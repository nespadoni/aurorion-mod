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

/**
 * Uma fotografia da cena; nenhum vertice ou particula trafega por tick.
 *
 * <p>Com {@code revealed} falso a identidade da casa vem neutra (sem nome, sem lema, cores e trilha
 * de rito generico): o resultado so sai do servidor no instante da revelacao, num segundo pacote.
 * Quem chega depois da revelacao ja recebe o inicio com a casa.
 */
public record RitePayload(UUID playerId, int entityId, boolean active, ResourceLocation dimension,
                          Vec3 anchor, int elapsed, boolean revealed, Component houseName, Component motto,
                          int color, RiteStyle style) implements CustomPacketPayload {
    public static final Type<RitePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AurorionEthereal.MOD_ID, "binding_rite"));

    /** As mesmas cores que o selo usa antes da revelacao; a trilha e a padrao, igual para toda casa. */
    public static final int NEUTRAL_COLOR = 0xD9E8FF;
    public static final RiteStyle NEUTRAL_STYLE = new RiteStyle(0x536A89, 0xFFF7DF, RiteStyle.DEFAULT.music());

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
                buf.writeBoolean(payload.revealed);
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
                    buf.readBoolean(),
                    ComponentSerialization.STREAM_CODEC.decode(buf), ComponentSerialization.STREAM_CODEC.decode(buf),
                    buf.readInt(), new RiteStyle(buf.readInt(), buf.readInt(), ResourceLocation.STREAM_CODEC.decode(buf))));

    /**
     * @param revealed falso antes da revelacao: a casa e trocada pela identidade neutra, e nada dela
     *                 (nem a trilha, que difere entre casas) chega ao cliente.
     */
    public static RitePayload start(ServerPlayer player, House house, Vec3 anchor, int elapsed, boolean revealed) {
        return revealed
                ? new RitePayload(player.getUUID(), player.getId(), true, player.level().dimension().location(),
                        anchor, elapsed, true, house.name(), house.motto(), house.color(), house.ceremony())
                : new RitePayload(player.getUUID(), player.getId(), true, player.level().dimension().location(),
                        anchor, elapsed, false, CommonComponents.EMPTY, CommonComponents.EMPTY,
                        NEUTRAL_COLOR, NEUTRAL_STYLE);
    }

    public static RitePayload end(ServerPlayer player) {
        return new RitePayload(player.getUUID(), player.getId(), false, player.level().dimension().location(),
                Vec3.ZERO, 0, false, CommonComponents.EMPTY, CommonComponents.EMPTY, NEUTRAL_COLOR, NEUTRAL_STYLE);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
