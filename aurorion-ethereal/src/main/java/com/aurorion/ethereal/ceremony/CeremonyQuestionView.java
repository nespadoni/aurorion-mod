package com.aurorion.ethereal.ceremony;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;

/**
 * Uma pergunta como o jogador a ve: o enunciado e os textos das alternativas, nada mais.
 *
 * <p>E um tipo separado de {@link CeremonyQuestion} em vez de um {@code StreamCodec} parcial dele
 * porque a omissao aqui e a regra do jogo, nao uma economia de bytes: se a casa de cada opcao
 * couber no pacote, ela chega no cliente, e a cerimonia deixa de ser uma cerimonia.
 */
public record CeremonyQuestionView(Component question, List<Component> options) {
    public static final StreamCodec<RegistryFriendlyByteBuf, CeremonyQuestionView> STREAM_CODEC = StreamCodec.composite(
            ComponentSerialization.STREAM_CODEC, CeremonyQuestionView::question,
            ComponentSerialization.STREAM_CODEC.apply(ByteBufCodecs.list()), CeremonyQuestionView::options,
            CeremonyQuestionView::new);
}
