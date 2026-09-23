package com.aurorion.magia.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Config do cliente, em {@code config/aurorion/magia-client.toml}. Tudo aqui e conforto visual: nada
 * muda regra de jogo, e o servidor nunca le este arquivo.
 */
public final class MagiaClientConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue VISUAL_DISTANCE;
    public static final ModConfigSpec.DoubleValue CAMERA_SHAKE;

    public static final ModConfigSpec SPEC;

    static {
        VISUAL_DISTANCE = BUILDER
                .comment(
                        "Distancia maxima (blocos) em que feixes e auras de magia sao desenhados.",
                        "O teto e 32 porque o vanilla ja descarta particula alem disso.")
                .defineInRange("visualDistance", 32, 8, 32);

        CAMERA_SHAKE = BUILDER
                .comment(
                        "Intensidade do tremor de camera do Dolor Cruciatus. 0 desliga.",
                        "Tambem e multiplicado pela opcao 'Efeitos de distorcao' do vanilla (acessibilidade).")
                .defineInRange("cameraShake", 1.0, 0.0, 2.0);

        SPEC = BUILDER.build();
    }

    private MagiaClientConfig() {
    }
}
