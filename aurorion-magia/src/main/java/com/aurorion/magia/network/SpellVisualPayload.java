package com.aurorion.magia.network;

import com.aurorion.magia.AurorionMagia;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/**
 * "Desenhe o visual X, ligado a estas entidades e a este ponto, por N ticks." E so isso que trafega.
 *
 * <p>Uns 30 bytes, uma vez por conjuracao (o Cruciatus e a Mao do Algoz reenviam a cada meio
 * segundo enquanto canalizam). O cliente acompanha as entidades pelo id de rede e gera particulas e
 * geometria sozinho. Particula nenhuma viaja pela rede — o servidor nunca chama
 * {@code sendParticles} nas nossas magias.
 *
 * @param casterId id de rede de quem conjurou, ou -1
 * @param targetId id de rede do alvo, ou -1 para visual preso so a um ponto (lacre, Lux Vorata)
 * @param ttl      ticks de vida do visual no cliente
 * @param pos      ponto do mundo: ancora, bloco lacrado, centro da zona, ou vetor de impulso —
 *                 depende do {@link Kind}
 * @param extra    raio, face do bloco ou intensidade — depende do {@link Kind}
 */
public record SpellVisualPayload(Kind kind, int casterId, int targetId, int ttl, Vec3 pos, float extra)
        implements CustomPacketPayload {
    public static final Type<SpellVisualPayload> TYPE = new Type<>(AurorionMagia.id("spell_visual"));

    private static final StreamCodec<ByteBuf, Kind> KIND_CODEC =
            ByteBufCodecs.BYTE.map(b -> Kind.byId(b), kind -> (byte) kind.ordinal());

    private static final StreamCodec<ByteBuf, Vec3> VEC3_CODEC = StreamCodec.of(
            (buf, vec) -> {
                buf.writeDouble(vec.x);
                buf.writeDouble(vec.y);
                buf.writeDouble(vec.z);
            },
            buf -> new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()));

    public static final StreamCodec<ByteBuf, SpellVisualPayload> STREAM_CODEC = StreamCodec.composite(
            KIND_CODEC, SpellVisualPayload::kind,
            ByteBufCodecs.VAR_INT, SpellVisualPayload::casterId,
            ByteBufCodecs.VAR_INT, SpellVisualPayload::targetId,
            ByteBufCodecs.VAR_INT, SpellVisualPayload::ttl,
            VEC3_CODEC, SpellVisualPayload::pos,
            ByteBufCodecs.FLOAT, SpellVisualPayload::extra,
            SpellVisualPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Novos tipos entram <b>sempre no fim</b>: o ordinal e o que vai na rede, e um cliente com o jar
     * velho leria o tipo errado. Mudou a ordem ou o formato, suba a versao em {@link MagiaNetwork}.
     */
    public enum Kind {
        /** Feixe vermelho e negro da mao ao peito, e o selo de sangue sob o alvo. */
        CRUCIATUS_BEAM,
        /** Espiral descendo no impacto e coroa jade na cabeca enquanto durar o dominio. */
        IMPERIUM_AURA,
        /** Circulo de alcance na ancora; a corrente aparece quando o preso se afasta. pos = ancora, extra = raio. */
        VINCULUM,
        /** A corrente estalando: puxao ou teleporte negado. */
        VINCULUM_SNAP,
        /** Movimento sendo sugado do alvo para a mao. */
        FURTUM_STEAL,
        /** Orbe de impulso girando na mao enquanto guardado. */
        FURTUM_HELD,
        /** Onda de choque no alvo que recebeu o impulso. pos = vetor do impulso. */
        FURTUM_RELEASE,
        /** Dois selos do End, um em cada ponta da troca. pos = onde o conjurador estava. */
        TRANSPOSITIO,
        /** Anel telecinetico em volta do alvo segurado e os filamentos ate a mao. */
        MANUS_GRIP,
        /** Mao espectral arrancando o item. */
        MANUS_VACUA,
        /** Aneis descendo e o selo no chao sob quem ajoelhou. */
        GENUA_FLECTE,
        /** Colar negro na garganta. */
        VOX_INTERDICTA,
        /** Selo desenhado na face do bloco. pos = ponto atingido, extra = face (ordinal de Direction). */
        SIGILLUM,
        /** Selo que recusou alguem: clarao. Mesmo pos/extra do SIGILLUM. */
        SIGILLUM_DENY,
        /** Selo quebrado: estilhacos. */
        SIGILLUM_BREAK,
        /** Selo descendo sobre o alvo e onda no chao ao aterrissar. */
        DEIECTIO,
        /** Zona que devorou a luz: escuridao no chao e neblina negra para quem esta dentro. pos = centro, extra = raio. */
        LUX_VORATA,
        /** Correntes girando em volta do corpo. */
        FERRUM_LIGATUM,
        /** Relogio parado no chao e geada no raio. pos = centro, extra = raio. */
        TEMPUS_SISTERE,
        /** Raio verde ate o alvo e o selo da morte onde ele caiu. pos = pes do alvo, extra = altura dele. */
        MORTEM_DICO,
        /** Selo de sangue enorme sob quem conjura e feixes ate cada suspenso. Segue o conjurador; extra = raio. */
        DOLOR_UNIVERSUS;

        private static final Kind[] VALUES = values();

        static Kind byId(int id) {
            return id >= 0 && id < VALUES.length ? VALUES[id] : CRUCIATUS_BEAM;
        }

        /** Visuais presos a um ponto: identificados pela posicao, nao pelas entidades. */
        public boolean anchoredToPoint() {
            return this == SIGILLUM || this == SIGILLUM_DENY || this == SIGILLUM_BREAK || this == LUX_VORATA
                    || this == TEMPUS_SISTERE || this == MORTEM_DICO;
        }
    }
}
