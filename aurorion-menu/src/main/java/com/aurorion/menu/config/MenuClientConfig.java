package com.aurorion.menu.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** {@code config/aurorion/menu-client.toml}: só o tamanho e a distância do painel, de cada um. */
public final class MenuClientConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.DoubleValue WIDTH, DISTANCE, FOLLOW_DEGREES;
    public static final ModConfigSpec.BooleanValue GESTURE, ADVANCEMENTS;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("painel");
        WIDTH = b.comment("Largura do painel do menu no mundo, em blocos.").defineInRange("largura", 2.25, 1.0, 5.0);
        DISTANCE = b.comment("Distancia do painel ate os olhos, em blocos.").defineInRange("distancia", 1.35, 0.8, 4.0);
        FOLLOW_DEGREES = b.comment("Quanto a cabeca pode virar (graus) antes de o painel vir junto.")
                .defineInRange("grausAteSeguir", 38.0, 10.0, 90.0);
        b.pop().push("geral");
        GESTURE = b.comment("Abrir o menu com o gesto (maos vazias, botao direito, puxar a mira para baixo).").define("gesto", true);
        ADVANCEMENTS = b.comment("Conquistas no cartao na frente da pessoa (desligado: o aviso do canto do vanilla).",
                "Desligue se outro mod de avisos de conquista estiver em uso.").define("conquistasNoCartao", true);
        b.pop();
        SPEC = b.build();
    }

    private MenuClientConfig() {
    }
}
