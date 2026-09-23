package com.aurorion.magia.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Config do servidor, em {@code config/aurorion/magia-server.toml}. */
public final class MagiaConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue STAFF_BYPASS;
    public static final ModConfigSpec.BooleanValue AUTHORITATIVE;
    public static final ModConfigSpec.IntValue DOMINATION_RADIUS;

    public static final ModConfigSpec SPEC;

    static {
        BUILDER.comment("Liberacao de magias por aula/RP.").push("liberacao");

        STAFF_BYPASS = BUILDER
                .comment(
                        "Staff (permissao 2+) conjura qualquer magia sem liberacao. Serve para ensaiar aula e",
                        "testar; desligue se a staff tambem joga personagem com progressao propria.")
                .define("staffBypass", true);

        AUTHORITATIVE = BUILDER
                .comment(
                        "A lista do Aurorion e a unica fonte de verdade.",
                        "true: o que o jogador aprendeu por fora (manuscrito do Iron's Restrictions, magias",
                        "  padrao dele, eldritch pesquisado) e esquecido no proximo login ou comando, e nao conjura.",
                        "false: o aprendido por fora continua valendo junto com as liberacoes do Aurorion.",
                        "Com true, deixe DefaultLearntSpells vazio no config do Iron's Restrictions.")
                .define("authoritative", true);

        BUILDER.pop();
        BUILDER.comment("Imperium Mentis.").push("imperium");

        DOMINATION_RADIUS = BUILDER
                .comment(
                        "Raio (blocos) em que um mob dominado procura o proximo alvo. A busca roda uma vez",
                        "por segundo e so em mob dominado; o custo cresce com o volume do raio.")
                .defineInRange("dominationRadius", 12, 4, 24);

        BUILDER.pop();
        SPEC = BUILDER.build();
    }

    private MagiaConfig() {
    }
}
