package com.aurorion.talk.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Config do lado cliente, gravada em {@code config/aurorion_talk-client.toml}.
 * Todos os valores sao lidos ao vivo, entao editar o arquivo e recarregar o mundo ja aplica.
 */
public final class TalkConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // --- Chat ---
    public static final ModConfigSpec.BooleanValue HIDE_PLAYER_CHAT;
    public static final ModConfigSpec.BooleanValue HIDE_ONLY_WHEN_BALLOON_SHOWN;

    // --- Baloes ---
    public static final ModConfigSpec.BooleanValue SHOW_OWN_BALLOON;
    public static final ModConfigSpec.DoubleValue HEIGHT_OFFSET;
    public static final ModConfigSpec.IntValue DISTANCE_BETWEEN_BALLOONS;
    public static final ModConfigSpec.IntValue MAX_BALLOONS;
    public static final ModConfigSpec.IntValue MIN_BALLOON_WIDTH;
    public static final ModConfigSpec.IntValue MAX_BALLOON_WIDTH;
    public static final ModConfigSpec.IntValue BALLOON_AGE_SECONDS;

    public static final ModConfigSpec SPEC;

    static {
        BUILDER.comment("Como o chat dos jogadores aparece no HUD.").push("chat");

        // Isto e so aparencia. Quem recebe cada fala e decidido pelo servidor (TalkServerConfig,
        // secao proximity): desligar aqui mostra no HUD as falas que ja chegaram, nunca as de longe.
        HIDE_PLAYER_CHAT = BUILDER
                .comment("Remove as mensagens de jogadores do HUD do chat (elas viram apenas baloes).",
                        "Mensagens de sistema, comandos, /msg, /me, morte e entrada/saida continuam visiveis.",
                        "Nao muda o que voce recebe: quem esta longe demais o servidor nem entrega.")
                .define("hidePlayerChat", true);

        // Padrao true: se o servidor entregou a fala, voce esta no alcance dela. Sem balao (quem
        // falou esta fora da distancia de renderizacao, ou e a staff espiando de longe), esconder
        // seria jogar fora a unica copia da mensagem.
        HIDE_ONLY_WHEN_BALLOON_SHOWN = BUILDER
                .comment("Se true, so esconde a mensagem quando um balao foi realmente criado.",
                        "Evita perder falas de quem esta fora do alcance de renderizacao.")
                .define("hideOnlyWhenBalloonShown", true);

        BUILDER.pop();
        BUILDER.comment("Aparencia e comportamento dos baloes.").push("balloons");

        SHOW_OWN_BALLOON = BUILDER
                .comment("Mostrar balao acima do proprio jogador (visivel em terceira pessoa).")
                .define("showOwnBalloon", true);

        HEIGHT_OFFSET = BUILDER
                .comment("Altura do balao acima da cabeca do jogador, em blocos.")
                .defineInRange("heightOffset", 0.9D, -4.0D, 8.0D);

        DISTANCE_BETWEEN_BALLOONS = BUILDER
                .comment("Espaco vertical entre baloes empilhados, em pixels.")
                .defineInRange("distanceBetweenBalloons", 3, 0, 32);

        MAX_BALLOONS = BUILDER
                .comment("Quantos baloes podem ficar empilhados acima de um jogador.")
                .defineInRange("maxBalloons", 5, 2, 20);

        MIN_BALLOON_WIDTH = BUILDER
                .comment("Largura minima do balao, em pixels.")
                .defineInRange("minBalloonWidth", 13, 5, 512);

        MAX_BALLOON_WIDTH = BUILDER
                .comment("Largura maxima antes do texto quebrar em varias linhas, em pixels.")
                .defineInRange("maxBalloonWidth", 180, 20, 512);

        BALLOON_AGE_SECONDS = BUILDER
                .comment("Por quantos segundos o balao fica na tela.")
                .defineInRange("balloonAgeSeconds", 15, 1, 600);

        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    private TalkConfig() {
    }
}
