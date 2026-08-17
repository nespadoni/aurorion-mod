package com.aurorion.vidas.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Config do lado cliente, gravada em {@code config/aurorion_vidas-client.toml}.
 *
 * <p>Só aparencia. Desligar o HUD nao muda regra nenhuma: as vidas continuam sendo gastas, o
 * servidor continua mandando o valor, e {@code /vidas} continua respondendo.
 */
public final class LivesClientConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue SHOW_HUD;

    public static final ModConfigSpec SPEC;

    static {
        BUILDER.comment("Aparencia do contador de vidas.").push("hud");

        SHOW_HUD = BUILDER
                .comment("Mostra os icones de vida logo acima da barra de fome.")
                .define("showHud", true);

        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    private LivesClientConfig() {
    }
}
