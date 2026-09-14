package com.aurorion.limbo.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import java.util.List;

/** Server-authored story; distributed once when the finale starts. */
public final class FinaleConfig {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();
    public static final ModConfigSpec.IntValue RISE_SECONDS = B.defineInRange("subidaEmSegundos", 25, 5, 120);
    public static final ModConfigSpec.IntValue PHRASE_SECONDS = B.defineInRange("fraseEmSegundos", 15, 5, 120);
    public static final ModConfigSpec.IntValue CREDITS_SECONDS = B.defineInRange("creditosEmSegundos", 140, 20, 1800);
    public static final ModConfigSpec.ConfigValue<String> MUSIC = B.comment("Evento de sounds.json, substituivel por resource pack.")
            .define("musica", "aurorion_limbo:finale", v -> v instanceof String s && s.length() <= 256
                    && net.minecraft.resources.ResourceLocation.tryParse(s) != null);
    public static final ModConfigSpec.ConfigValue<String> PHRASE = B.define("frase",
            "A vida é um sopro.\nEu deixo de existir aqui...\nmas houve um instante em que o mundo me chamou pelo nome.",
            v -> v instanceof String s && s.length() <= 512);
    public static final ModConfigSpec.ConfigValue<List<? extends String>> PARAGRAPHS = B
            .comment("Paragrafos em rolagem. Depois deles: Voce esta morto, por 60 segundos, e desconexao.")
            .defineListAllowEmpty("paragrafos", List.of(
                    "No princípio, havia silêncio. E agora o silêncio aprende, outra vez, a ocupar o meu lugar.",
                    "Eu pensei que o mundo perceberia. Que as árvores se curvariam. Que o céu hesitaria antes de amanhecer.",
                    "Mas a chuva continuará caindo sobre os caminhos que não vou terminar. Haverá luz nas janelas. Haverá passos depois dos meus.",
                    "É estranho desaparecer de um mundo tão grande. Ser uma presença tão pequena que o infinito não precisa parar para nos perder.",
                    "As coisas que eu guardei ficarão sem dono. As palavras que adiei não encontrarão mais a minha voz.",
                    "Talvez um dia alguém passe por aqui e sinta uma saudade que não sabe explicar. Talvez ninguém se lembre.",
                    "Eu tive medo de que isso tornasse tudo vazio. De que uma história esquecida fosse uma história que nunca aconteceu.",
                    "Mas eu estive aqui. Houve uma mão que alcancei. Uma noite que atravessei. Um instante pequeno demais para a eternidade, inteiro demais para mim.",
                    "Não levarei nada. Nem o nome, nem a dor, nem a esperança de que a próxima porta se abra.",
                    "Só este último sopro. Só este adeus que o mundo não precisa ouvir para ser verdadeiro.",
                    "Se ainda houver alguém do outro lado do silêncio: continue. Faça do amanhã aquilo que eu já não posso fazer.",
                    "O céu não vai guardar o meu lugar. Mas, por um breve instante, eu fiz parte dele.",
                    "E então... já não há passos. Já não há espera. Já não há eu."
            ), () -> "", v -> v instanceof String s && s.length() <= 512);
    public static final ModConfigSpec SPEC = B.build();
    private FinaleConfig() { }
}
