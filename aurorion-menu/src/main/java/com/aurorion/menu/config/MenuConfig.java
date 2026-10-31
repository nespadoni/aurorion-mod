package com.aurorion.menu.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** {@code config/aurorion/menu-server.toml}. */
public final class MenuConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue PRESENCE, MEDITATION;
    public static final ModConfigSpec.IntValue HEAL_SECONDS, HOSTILE_RADIUS, COMBAT_SECONDS;
    public static final ModConfigSpec.DoubleValue HEAL_AMOUNT;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("presenca");
        PRESENCE = b.comment("Quem esta perto ve o braco erguido e o painel holografico borrado de quem abriu o menu.")
                .define("habilitada", true);
        b.pop().push("meditar");
        MEDITATION = b.comment("Interacoes > Meditar: a pose (emote do Emotecraft, ou a pose sentada sem ele) e a",
                "recuperacao lenta de vida.").define("habilitado", true);
        HEAL_SECONDS = b.comment("A cada quantos segundos meditando a pessoa recupera vida.").defineInRange("segundosCura", 3, 1, 60);
        HEAL_AMOUNT = b.comment("Quanta vida (meio coracao = 1) a cada vez.").defineInRange("cura", 1.0, 0.0, 20.0);
        HOSTILE_RADIUS = b.comment("Nao da para meditar com monstro hostil ate esta distancia.").defineInRange("raioHostil", 8, 0, 64);
        COMBAT_SECONDS = b.comment("Nem tendo levado dano ha menos que isto (segundos).").defineInRange("segundosForaDeCombate", 10, 0, 300);
        b.pop();
        SPEC = b.build();
    }

    private MenuConfig() {
    }
}
