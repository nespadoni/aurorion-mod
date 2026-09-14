package com.aurorion.ethereal.network;

import com.aurorion.ethereal.AurorionEthereal;
import com.aurorion.ethereal.ceremony.CeremonyQuestionView;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Servidor -> cliente: abre a cerimonia com todas as perguntas de uma vez.
 *
 * <p>Todas de uma vez, e nao uma por vez, para que trocar de pergunta seja instantaneo em vez de um
 * ida-e-volta pela rede: quem responde nao fica olhando uma tela travada entre uma pergunta e a
 * seguinte. O servidor continua sendo a autoridade — ele guarda em que pergunta o jogador esta e
 * recusa resposta fora de ordem; o pacote adiantado so evita a espera.
 *
 * <p>Nenhuma casa viaja aqui. Ver {@link CeremonyQuestionView}.
 *
 * @param startAt em que pergunta abrir. Vale zero na abertura e, no retorno de quem fechou a tela no
 *                meio, a pergunta em que o <b>servidor</b> diz que ele parou — nunca a que o cliente
 *                acha que era.
 */
public record OpenCeremonyPayload(List<CeremonyQuestionView> questions, int startAt) implements CustomPacketPayload {
    public static final Type<OpenCeremonyPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AurorionEthereal.MOD_ID, "open_ceremony"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenCeremonyPayload> STREAM_CODEC = StreamCodec.composite(
            CeremonyQuestionView.STREAM_CODEC.apply(ByteBufCodecs.list()), OpenCeremonyPayload::questions,
            ByteBufCodecs.VAR_INT, OpenCeremonyPayload::startAt,
            OpenCeremonyPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
