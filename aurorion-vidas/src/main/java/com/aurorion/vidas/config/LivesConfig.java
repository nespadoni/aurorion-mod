package com.aurorion.vidas.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Config do lado servidor, gravada em {@code config/aurorion_vidas-server.toml}.
 *
 * <p>O <b>ponto</b> de exilio nao esta aqui de proposito: ele e uma coordenada no mundo, então mora
 * no save (ajustado com {@code /vidas exilio aqui}) e nao num arquivo de texto. Config que guarda
 * coordenada envelhece mal — some quando o mundo e trocado e nao acompanha o que foi construido.
 */
public final class LivesConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue MAX_LIVES;
    public static final ModConfigSpec.ConfigValue<String> EXILE_DIMENSION;
    public static final ModConfigSpec.BooleanValue BLOCK_EXILE_EXIT;
    public static final ModConfigSpec.BooleanValue IGNORE_CREATIVE;
    public static final ModConfigSpec.BooleanValue ANNOUNCE_EXILE;
    public static final ModConfigSpec.BooleanValue ANNOUNCE_LIFE_LOSS;

    public static final ModConfigSpec SPEC;

    static {
        BUILDER.comment("Sistema de vidas do Aurorion.").push("vidas");

        MAX_LIVES = BUILDER
                .comment(
                        "Quantas vidas cada jogador tem. E tambem quantos icones aparecem no HUD, entao",
                        "valores muito altos comecam a competir com a barra de fome por espaco na tela."
                )
                .defineInRange("maxLives", 5, 1, 20);

        IGNORE_CREATIVE = BUILDER
                .comment("Morte em criativo ou espectador nao gasta vida.")
                .define("ignoreCreative", true);

        BUILDER.pop();
        BUILDER.comment("O que acontece quando as vidas acabam.").push("exilio");

        EXILE_DIMENSION = BUILDER
                .comment(
                        "Para onde vai quem zerou as vidas.",
                        "O exilio so tem peso se essa dimensao for dificil de sair — e para isso que existe o",
                        "aurorion_portais mantendo o Nether trancado. Sem ele, o exilado sai pelo primeiro",
                        "portal que encontrar.",
                        "O ponto exato de chegada NAO se configura aqui: use /vidas exilio aqui, no lugar."
                )
                .define("exileDimension", "aurorion_limbo:limbo");

        BLOCK_EXILE_EXIT = BUILDER
                .comment(
                        "Quem esta exilado nao consegue sair da dimensao de exilio de jeito nenhum — nem",
                        "quando um trem do aurorion_portais abre a passagem para todo mundo.",
                        "E o que da sentido a taxa de resgate: se o exilado pudesse pegar o trem junto com os",
                        "outros, bastaria esperar o sabado a noite e ninguem pagaria nada.",
                        "A saida continua sendo devolver vida a pessoa (/vidas dar, ou o item de resgate)."
                )
                .define("blockExileExit", true);

        BUILDER.pop();
        BUILDER.comment("Avisos.").push("avisos");

        // Chave nova ("announceExileInChat", padrao false) no lugar de "announceExile" (padrao true):
        // morte e exilio nao aparecem mais no chat de quem estava longe. Trocar a chave faz o arquivo
        // ja gravado nos servidores assumir o padrao novo sozinho.
        ANNOUNCE_EXILE = BUILDER
                .comment(
                        "Anuncia no chat de TODO MUNDO (com som) quando alguem e exilado.",
                        "Falso (padrao): o que acontece longe de alguem nao aparece para ele. Quem foi exilado",
                        "recebe o aviso dele de qualquer forma, em particular.")
                .define("announceExileInChat", false);

        ANNOUNCE_LIFE_LOSS = BUILDER
                .comment(
                        "Anuncia no chat de TODO MUNDO cada vida perdida.",
                        "Falso (padrao) porque com 90 jogadores morte e rotina, e o vanilla ja anuncia a morte",
                        "em si. Quem perdeu a vida e avisado de qualquer forma, em particular."
                )
                .define("announceLifeLoss", false);

        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    private LivesConfig() {
    }
}
