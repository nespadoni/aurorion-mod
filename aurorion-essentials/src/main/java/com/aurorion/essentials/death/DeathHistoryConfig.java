package com.aurorion.essentials.death;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class DeathHistoryConfig {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();
    public static final ModConfigSpec.BooleanValue ENABLED = B
            .comment("Salva mortes para consulta e recuperacao administrativa. Nao altera drops ou keepInventory.")
            .define("enabled", true);
    public static final ModConfigSpec.IntValue RETENTION = B
            .comment("Maximo de mortes por jogador. Backups de restauracao sao conservados separadamente.")
            .defineInRange("deathsPerPlayer", 100, 10, 1000);
    public static final ModConfigSpec SPEC = B.build();
    private DeathHistoryConfig() { }
}
