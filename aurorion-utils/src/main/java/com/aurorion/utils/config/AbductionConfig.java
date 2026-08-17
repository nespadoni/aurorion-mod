package com.aurorion.utils.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Config do lado servidor, gravada em {@code config/aurorion_utils-server.toml}. Cada valor e
 * "congelado" na {@link com.aurorion.utils.entity.AbductionBeamEntity} no momento do spawn — ver
 * {@link com.aurorion.utils.abduction.AbductionManager} — entao editar aqui so afeta abducoes
 * novas, nunca uma que ja esta em andamento.
 */
public final class AbductionConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue HOLD_DURATION_TICKS;
    public static final ModConfigSpec.IntValue ASCENT_HEIGHT_BLOCKS;
    public static final ModConfigSpec.IntValue ASCENT_DURATION_TICKS;
    public static final ModConfigSpec.IntValue RETRACT_DURATION_TICKS;
    public static final ModConfigSpec.DoubleValue BEAM_RADIUS;
    public static final ModConfigSpec.ConfigValue<String> BEAM_COLOR_RGB;

    public static final ModConfigSpec SPEC;

    static {
        BUILDER.comment(
                "Comando /abduzir: puxa um jogador ate onde o executor (ou um terceiro) esta, com",
                "uma animacao de feixe de luz. O jogador sobe parado dentro do feixe e so entao e",
                "teleportado de fato."
        ).push("abduction");

        HOLD_DURATION_TICKS = BUILDER
                .comment(
                        "Quanto tempo o jogador fica parado dentro do feixe (ja com altura cheia, som",
                        "tocando) antes de comecar a subir. Em ticks (20 ticks = 1 segundo)."
                )
                .defineInRange("holdDurationTicks", 40, 0, 1200);

        ASCENT_HEIGHT_BLOCKS = BUILDER
                .comment(
                        "Quantos blocos o jogador sobe dentro do feixe antes do teleporte de fato.",
                        "Se houver um teto solido antes dessa altura, a subida para um pouco abaixo dele",
                        "-- entao debaixo de um telhado a subida e sempre curta, por mais alto que este",
                        "valor esteja."
                )
                .defineInRange("ascentHeightBlocks", 40, 1, 320);

        ASCENT_DURATION_TICKS = BUILDER
                .comment("Duracao da subida, em ticks (20 ticks = 1 segundo).")
                .defineInRange("ascentDurationTicks", 60, 10, 1200);

        RETRACT_DURATION_TICKS = BUILDER
                .comment(
                        "Depois que o jogador ja foi teleportado, quanto tempo o feixe (sem ninguem",
                        "dentro) leva pra recolher de baixo pra cima e sumir. Em ticks."
                )
                .defineInRange("retractDurationTicks", 20, 1, 200);

        BEAM_RADIUS = BUILDER
                .comment("Raio do feixe: largura visual e raio de repulsao de quem nao e o abduzido.")
                .defineInRange("beamRadius", 1.5, 0.5, 8.0);

        BEAM_COLOR_RGB = BUILDER
                .comment(
                        "Cor padrao do feixe, usada quando /abduzir e chamado sem o argumento <cor>.",
                        "Aceita hexadecimal RRGGBB (com ou sem #) ou um nome da paleta do comando:",
                        "roxo, lilas, magenta, rosa, vermelho, carmesim, laranja, dourado, amarelo,",
                        "lima, verde, turquesa, ciano, azul, anil, cinza, branco."
                )
                .define("beamColorRgb", "9B30FF");

        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    private AbductionConfig() {
    }
}
