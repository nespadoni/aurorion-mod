package com.aurorion.essentials.cleanup;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Config do lado servidor, gravada em {@code config/aurorion_essentials-server.toml}. Os valores
 * sao relidos a cada tick pelo {@link CleanupScheduler} — nao precisa reiniciar para aplicar.
 */
public final class CleanupConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ENABLED;
    public static final ModConfigSpec.IntValue INTERVAL_MINUTES;
    public static final ModConfigSpec.IntValue WARNING_SECONDS_BEFORE;
    public static final ModConfigSpec.BooleanValue CLEAN_ITEMS;
    public static final ModConfigSpec.BooleanValue CLEAN_EXPERIENCE_ORBS;

    public static final ModConfigSpec SPEC;

    static {
        BUILDER.comment(
                "Limpeza periodica de itens dropados e orbs de XP acumulados no chao.",
                "Existe para nao deixar entidades ociosas se acumulando com o servidor rodando por horas",
                "(fazendas AFK, mobs mortos em massa etc.) — nunca mexe em mobs, veiculos ou blocos."
        ).push("cleanup");

        ENABLED = BUILDER
                .comment("Liga/desliga a limpeza periodica inteira.")
                .define("enabled", true);

        INTERVAL_MINUTES = BUILDER
                .comment("De quanto em quanto tempo a limpeza roda, em minutos.")
                .defineInRange("intervalMinutes", 60, 5, 1440);

        WARNING_SECONDS_BEFORE = BUILDER
                .comment(
                        "Quantos segundos antes da limpeza o aviso (actionbar) aparece.",
                        "Se maior ou igual ao intervalo, o aviso dispara logo no inicio do ciclo."
                )
                .defineInRange("warningSecondsBefore", 60, 0, 600);

        CLEAN_ITEMS = BUILDER
                .comment("Remover itens dropados (ItemEntity) no chao a cada ciclo.")
                .define("cleanItems", true);

        CLEAN_EXPERIENCE_ORBS = BUILDER
                .comment("Remover orbs de XP (ExperienceOrb) no chao a cada ciclo.")
                .define("cleanExperienceOrbs", true);

        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    private CleanupConfig() {
    }
}
