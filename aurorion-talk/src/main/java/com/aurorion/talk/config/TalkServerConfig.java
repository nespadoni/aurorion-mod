package com.aurorion.talk.config;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/**
 * Config do lado servidor, gravada em {@code config/aurorion/talk-server.toml}.
 *
 * <p>Por que existe um segundo arquivo: o {@link TalkConfig} e do cliente, e cliente e opiniao —
 * quem nao gosta desliga, e quem nao tem o mod nunca leu nada daquilo. A proibicao do sussurro nao
 * pode ser assim. Uma regra que so vale para quem instalou o mod nao e regra: o jogador que entra
 * com o cliente limpo continuaria mandando {@code /w} a vontade. Por isso ela mora aqui, onde o
 * servidor recusa o comando para todo mundo igual.
 */
public final class TalkServerConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue BLOCK_PRIVATE_MESSAGES;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> PRIVATE_MESSAGE_COMMANDS;
    public static final ModConfigSpec.IntValue BYPASS_PERMISSION_LEVEL;
    public static final ModConfigSpec.ConfigValue<String> DENY_MESSAGE;

    public static final ModConfigSpec SPEC;

    static {
        BUILDER.comment("Quem pode falar sem aparecer acima da propria cabeca.").push("privateMessages");

        BLOCK_PRIVATE_MESSAGES = BUILDER
                .comment("Recusa os comandos de mensagem privada (/msg, /tell, /w) para jogadores comuns.",
                        "O mod existe para que falar seja um ato visivel; o sussurro e a porta dos fundos",
                        "disso — entrega a fala a uma pessoa so, sem balao nenhum na tela de quem esta perto.")
                .define("blockPrivateMessages", true);

        PRIVATE_MESSAGE_COMMANDS = BUILDER
                .comment("Raizes de comando recusadas. Barra inicial e maiusculas sao ignoradas.",
                        "As tres do padrao sao o mesmo comando do vanilla: /tell e /w sao apelidos de /msg,",
                        "e a lista precisa citar os tres porque o servidor barra pelo que foi digitado.",
                        "Acrescente \"teammsg\" e \"tm\" se o chat de time tambem for um jeito de conversar",
                        "sem ninguem em volta perceber, ou o comando de sussurro de algum outro mod do pack.",
                        "Lista vazia nao barra nada — mas para desligar de vez use blockPrivateMessages,",
                        "que diz a intencao em vez de deixar uma lista vazia parecendo esquecimento.")
                .defineListAllowEmpty("commands", List.of("msg", "tell", "w"),
                        () -> "msg",
                        entry -> entry instanceof String text && !text.isBlank());

        BYPASS_PERMISSION_LEVEL = BUILDER
                .comment("Nivel de permissao que continua podendo sussurrar. 2 e o OP comum da staff.",
                        "Moderar as vezes exige falar com uma pessoa so, sem a cena inteira ouvindo.",
                        "0 libera todo mundo (equivale a desligar); 5 tranca ate para OP.")
                .defineInRange("bypassPermissionLevel", 2, 0, 5);

        DENY_MESSAGE = BUILDER
                .comment("O que o jogador recusado le. Vazio recusa em silencio — desaconselhado:",
                        "sem aviso, o comando engolido parece travamento do servidor.")
                .define("denyMessage",
                        "Em Aurorion ninguém sussurra. Fale — o que você disser aparece acima da sua cabeça.");

        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    private TalkServerConfig() {
    }
}
