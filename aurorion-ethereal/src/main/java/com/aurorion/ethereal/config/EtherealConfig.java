package com.aurorion.ethereal.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Config do lado servidor, gravada em {@code config/aurorion_ethereal-server.toml}.
 *
 * <p>O que <em>nao</em> esta aqui e tao proposital quanto o que esta: nome, cor, lema, icone e
 * lotacao de casa nao sao config, sao datapack ({@link com.aurorion.ethereal.house.House}). Config e
 * para regra de servidor; conteudo e para dado.
 */
public final class EtherealConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue CEREMONY_REQUIRED;
    public static final ModConfigSpec.BooleanValue NOTIFY_STAFF;
    public static final ModConfigSpec.BooleanValue ALLOW_RECHOOSE;
    public static final ModConfigSpec.BooleanValue ANNOUNCE_IN_CHAT;

    public static final ModConfigSpec SPEC;

    static {
        BUILDER.comment(
                "Casas de Ethereal. As casas em si (nome, lema, cor, icone, lotacao) sao definidas em",
                "data/<namespace>/aurorion/houses/*.json de um datapack — nao aqui."
        ).push("houses");

        CEREMONY_REQUIRED = BUILDER
                .comment(
                        "Verdadeiro (padrao) = quem define a casa e a staff, com /casa cerimonia <jogador> <casa>,",
                        "e o altar serve so para consultar a propria casa. O jogador nunca escolhe sozinho.",
                        "Falso = o altar volta a abrir a grade de casas e o jogador escolhe na hora. E a saida",
                        "para quando nao houver staff para conduzir as cerimonias."
                )
                .define("ceremonyRequired", true);

        NOTIFY_STAFF = BUILDER
                .comment(
                        "Avisa a staff online quando uma cerimonia termina e fica esperando decisao.",
                        "Desligue se o chat da staff ficar barulhento — o veredito e gravado em disco de",
                        "qualquer jeito, e /casa cerimonia pendentes lista o que esta esperando."
                )
                .define("notifyStaff", true);

        ALLOW_RECHOOSE = BUILDER
                .comment(
                        "Se o jogador pode voltar ao altar e refazer a vinculacao depois de ja ter casa.",
                        "Falso (padrao) = a casa e definitiva e so um admin desfaz, com /casa limpar."
                )
                .define("allowRechoose", false);

        ANNOUNCE_IN_CHAT = BUILDER
                .comment(
                        "Anuncia no chat do servidor quando alguem e acolhido por uma casa. Com 80 jogadores o",
                        "chat ja e barulhento — desligue se a vinculacao virar rotina em vez de evento."
                )
                .define("announceInChat", true);

        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    private EtherealConfig() {
    }
}
