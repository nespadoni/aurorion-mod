package com.aurorion.intencao.network;

import com.aurorion.intencao.AurorionIntencao;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/**
 * O que quem esta <b>em volta</b> ve: o selo de quem foi chamado, a onda de quem voltou e a aura do
 * vencedor. Vai para quem tem o chunk (ou a entidade) a vista; particula nenhuma viaja pela rede.
 *
 * @param entity id de rede da entidade (AURA), ou -1
 * @param ttl    ticks de vida do visual no cliente
 */
public record WorldPayload(Kind kind, int entity, Vec3 pos, int color, int ttl) implements CustomPacketPayload {
    public static final Type<WorldPayload> TYPE = new Type<>(AurorionIntencao.id("mundo"));

    private static final StreamCodec<ByteBuf, Kind> KIND = ByteBufCodecs.BYTE.map(
            b -> Kind.values()[Math.floorMod(b, Kind.values().length)], kind -> (byte) kind.ordinal());
    private static final StreamCodec<ByteBuf, Vec3> VEC3 = StreamCodec.of(
            (buf, vec) -> {
                buf.writeDouble(vec.x);
                buf.writeDouble(vec.y);
                buf.writeDouble(vec.z);
            },
            buf -> new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()));

    public static final StreamCodec<ByteBuf, WorldPayload> STREAM_CODEC = StreamCodec.composite(
            KIND, WorldPayload::kind,
            ByteBufCodecs.VAR_INT, WorldPayload::entity,
            VEC3, WorldPayload::pos,
            ByteBufCodecs.INT, WorldPayload::color,
            ByteBufCodecs.VAR_INT, WorldPayload::ttl,
            WorldPayload::new);

    /** Novos tipos sempre no fim: o ordinal vai na rede. */
    public enum Kind {
        /** Selo no chao e o puxao para cima, onde alguem acabou de ser chamado. */
        SEAL,
        /** Onda de choque onde alguem voltou do plano. */
        WAVE,
        /** Aura na cor da Casa em volta do vencedor. */
        AURA
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
