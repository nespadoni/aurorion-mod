package com.aurorion.ato2.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Config do lado servidor, gravada em {@code config/aurorion_ato2-server.toml}.
 *
 * <p>O que <em>nao</em> esta aqui e tao proposital quanto o que esta: nome, cor, icone e lotacao de
 * casa nao sao config, sao datapack ({@link com.aurorion.ato2.house.House}). Config e para regra de
 * servidor; conteudo e para dado.
 */
public final class HouseConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ALLOW_RECHOOSE;
    public static final ModConfigSpec.BooleanValue ANNOUNCE_IN_CHAT;

    public static final ModConfigSpec SPEC;

    static {
        BUILDER.comment(
                "Escolha de casa do Ato 2. As casas em si (nome, cor, icone, lotacao) sao definidas em",
                "data/<namespace>/aurorion/houses/*.json de um datapack, nao aqui."
        ).push("houses");

        ALLOW_RECHOOSE = BUILDER
                .comment(
                        "Se o jogador pode voltar ao altar e trocar de casa depois de ja ter escolhido.",
                        "Falso (padrao) = a escolha e definitiva e so um admin desfaz, com /casa limpar."
                )
                .define("allowRechoose", false);

        ANNOUNCE_IN_CHAT = BUILDER
                .comment(
                        "Anuncia no chat do servidor quando alguem escolhe uma casa. Com 80 jogadores o",
                        "chat ja e barulhento — desligue se a escolha virar rotina em vez de evento."
                )
                .define("announceInChat", true);

        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    private HouseConfig() {
    }
}
