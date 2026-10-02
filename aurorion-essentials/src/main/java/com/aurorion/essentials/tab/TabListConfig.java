package com.aurorion.essentials.tab;

import net.neoforged.neoforge.common.ModConfigSpec;

/** {@code config/aurorion/essentials-tablist-server.toml}. Lido ao vivo: nao precisa reiniciar. */
public final class TabListConfig {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();
    public static final ModConfigSpec.BooleanValue HOUSE_COLOR = B
            .comment("Pinta o nome de cada jogador na tab com a cor da casa dele (aurorion-ethereal).",
                    "So o trecho do nome muda: prefixo e sufixo de outro mod de tab (Just Essentials) ficam como estao.",
                    "Quem nao tem casa continua com a cor que ja tinha.")
            .define("houseColor", true);
    public static final ModConfigSpec SPEC = B.build();

    private TabListConfig() {
    }

    static boolean houseColor() {
        return SPEC.isLoaded() && HOUSE_COLOR.get();
    }
}
