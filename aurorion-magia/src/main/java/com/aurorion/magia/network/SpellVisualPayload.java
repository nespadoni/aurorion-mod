package com.aurorion.magia.network;

import com.aurorion.magia.AurorionMagia;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * "Desenhe este visual entre estas duas entidades por N ticks." E so isso que trafega.
 *
 * <p>Uns 6 a 10 bytes, e nenhuma posicao: o cliente acha as entidades pelo id de rede e acompanha o
 * movimento delas sozinho. Particula nenhuma viaja pela rede — o servidor nao chama
 * {@code sendParticles} nas nossas magias, que mandaria um pacote por particula para cada jogador
 * perto.
 *
 * @param ttl ticks de vida do visual no cliente. O Cruciatus reenvia a cada pulso da canalizacao
 *            com um ttl curto, entao o feixe some sozinho quando a canalizacao para — sem pacote de
 *            "parar".
 */
public record SpellVisualPayload(Kind kind, int casterId, int targetId, int ttl) implements CustomPacketPayload {
    public static final Type<SpellVisualPayload> TYPE = new Type<>(AurorionMagia.id("spell_visual"));

    private static final StreamCodec<ByteBuf, Kind> KIND_CODEC =
            ByteBufCodecs.BYTE.map(b -> Kind.byId(b), kind -> (byte) kind.ordinal());

    public static final StreamCodec<ByteBuf, SpellVisualPayload> STREAM_CODEC = StreamCodec.composite(
            KIND_CODEC, SpellVisualPayload::kind,
            ByteBufCodecs.VAR_INT, SpellVisualPayload::casterId,
            ByteBufCodecs.VAR_INT, SpellVisualPayload::targetId,
            ByteBufCodecs.VAR_INT, SpellVisualPayload::ttl,
            SpellVisualPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public enum Kind {
        /** Feixe vermelho e negro da mao do conjurador ao peito do alvo. */
        CRUCIATUS_BEAM,
        /** Espiral descendo no impacto e aura na cabeca enquanto durar o dominio. */
        IMPERIUM_AURA;

        private static final Kind[] VALUES = values();

        static Kind byId(int id) {
            return id >= 0 && id < VALUES.length ? VALUES[id] : CRUCIATUS_BEAM;
        }
    }
}
