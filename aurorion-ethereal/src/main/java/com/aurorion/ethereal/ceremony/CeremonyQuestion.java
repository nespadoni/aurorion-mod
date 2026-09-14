package com.aurorion.ethereal.ceremony;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Uma pergunta da Cerimonia de Vinculacao, com as opcoes e a casa que cada opcao puxa.
 *
 * <p>Pergunta e conteudo, igual casa: mora em
 * {@code data/<namespace>/aurorion/ceremony_questions/<nome>.json}. Reescrever uma pergunta, mudar a
 * ordem, acrescentar uma sexta opcao ou trocar o conjunto inteiro entre um ato e outro e editar
 * arquivo e dar {@code /reload}.
 *
 * <p><b>O vinculo opcao -> casa nunca sai do servidor.</b> O cliente recebe so os textos
 * ({@link CeremonyQuestionView}); se ele recebesse o mapeamento, qualquer jogador leria o pacote e
 * saberia a resposta "certa" para cair na casa que quisesse — e a cerimonia viraria um menu.
 *
 * @param weight quanto uma resposta pesa na contagem. Serve para uma pergunta final valer mais que
 *               as de aquecimento; o padrao e 1.
 */
public record CeremonyQuestion(
        ResourceLocation id,
        Component question,
        List<Option> options,
        int weight,
        int order
) {
    /** Uma alternativa e a casa que ela sugere. */
    public record Option(Component text, ResourceLocation house) {
        public static final Codec<Option> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ComponentSerialization.CODEC.fieldOf("text").forGetter(Option::text),
                ResourceLocation.CODEC.fieldOf("house").forGetter(Option::house)
        ).apply(instance, Option::new));
    }

    /**
     * Codec do arquivo. Como em {@link com.aurorion.ethereal.house.House}, o id vem do caminho do
     * arquivo e nao do conteudo.
     *
     * <p>O minimo de duas opcoes e validado aqui e nao no uso: uma pergunta com uma alternativa so
     * nao e uma pergunta, e descobrir isso no meio de uma cerimonia ao vivo seria pessimo.
     */
    public static Codec<CeremonyQuestion> codec(ResourceLocation id) {
        return RecordCodecBuilder.create(instance -> instance.group(
                ComponentSerialization.CODEC.fieldOf("question").forGetter(CeremonyQuestion::question),
                Option.CODEC.listOf(2, 8).fieldOf("options").forGetter(CeremonyQuestion::options),
                Codec.intRange(1, 100).optionalFieldOf("weight", 1).forGetter(CeremonyQuestion::weight),
                Codec.INT.optionalFieldOf("order", 0).forGetter(CeremonyQuestion::order)
        ).apply(instance, (question, options, weight, order) ->
                new CeremonyQuestion(id, question, options, weight, order)));
    }

    /** O que o cliente pode ver: os textos, sem as casas por tras deles. */
    public CeremonyQuestionView view() {
        return new CeremonyQuestionView(question, options.stream().map(Option::text).toList());
    }
}
