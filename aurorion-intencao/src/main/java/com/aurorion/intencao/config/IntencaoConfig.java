package com.aurorion.intencao.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** {@code config/aurorion/intencao-server.toml}. */
public final class IntencaoConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue PERMISSION_LEVEL;
    public static final ModConfigSpec.IntValue TIMEOUT_MINUTES;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        PERMISSION_LEVEL = builder
                .comment("Nivel de permissao para conduzir uma batalha (/intencao).")
                .defineInRange("permissionLevel", 2, 0, 4);
        TIMEOUT_MINUTES = builder
                .comment("Rede de seguranca: uma batalha esquecida aberta se desfaz sozinha (empate) depois disto.",
                        "Uma sessao de mesa longa cabe com folga no padrao.")
                .defineInRange("timeoutMinutes", 180, 10, 24 * 60);
        SPEC = builder.build();
    }

    private IntencaoConfig() {
    }
}
