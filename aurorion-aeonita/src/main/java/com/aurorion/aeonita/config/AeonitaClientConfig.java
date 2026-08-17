package com.aurorion.aeonita.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Config do lado cliente, gravada em {@code config/aurorion_aeonita-client.toml}. So existe porque a
 * luz dinamica e a unica parte deste mod que custa frame time: quem estiver com o modpack pesando
 * pode desligar so ela sem perder itens, blocos nem receitas.
 */
public final class AeonitaClientConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue DYNAMIC_LIGHT_ENABLED;
    public static final ModConfigSpec.IntValue DYNAMIC_LIGHT_LEVEL;

    public static final ModConfigSpec SPEC;

    static {
        BUILDER.comment(
                "Luz dinamica: itens da tag aurorion_aeonita:emits_light acendem onde estao (na mao,",
                "no chao ou num quadro). E uma ilusao local — o bloco de luz so existe na copia do",
                "mundo do seu cliente, nunca vai para o servidor nem para o save."
        ).push("dynamicLight");

        DYNAMIC_LIGHT_ENABLED = BUILDER
                .comment("Liga a luz dinamica. Desligue se o FPS estiver apertado: cada mudanca de posicao recalcula iluminacao do chunk.")
                .define("enabled", true);

        DYNAMIC_LIGHT_LEVEL = BUILDER
                .comment("Nivel de luz emitido, de 1 a 15 (15 = tocha forte).")
                .defineInRange("lightLevel", 15, 1, 15);

        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    private AeonitaClientConfig() {
    }
}
