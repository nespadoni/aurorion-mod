package com.aurorion.core.config;

import net.neoforged.fml.config.ModConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * O nome do arquivo de config e a unica regra do {@link AurorionConfigs} — e a que quebra em
 * silencio: um caminho errado nao da erro, gera outro arquivo com os padroes e joga fora os ajustes
 * de quem administra o servidor.
 */
class AurorionConfigsTest {
    @Test void dropsTheRedundantPrefix() {
        assertEquals("aurorion/limbo-server.toml",
                AurorionConfigs.fileName("aurorion_limbo", ModConfig.Type.SERVER, null));
    }

    @Test void variantSeparatesTwoFilesOfTheSameType() {
        assertEquals("aurorion/essentials-server.toml",
                AurorionConfigs.fileName("aurorion_essentials", ModConfig.Type.SERVER, null));
        assertEquals("aurorion/essentials-privacy-server.toml",
                AurorionConfigs.fileName("aurorion_essentials", ModConfig.Type.SERVER, "privacy"));
    }

    @Test void clientAndServerOfTheSameModNeverCollide() {
        String client = AurorionConfigs.fileName("aurorion_vidas", ModConfig.Type.CLIENT, null);
        String server = AurorionConfigs.fileName("aurorion_vidas", ModConfig.Type.SERVER, null);

        assertEquals("aurorion/vidas-client.toml", client);
        assertEquals("aurorion/vidas-server.toml", server);
    }

    /** Um mod fora da convencao de nome mantem o id inteiro, em vez de ficar sem nome nenhum. */
    @Test void modOutsideTheNamingConventionKeepsItsFullId() {
        assertEquals("aurorion/outro_mod-server.toml",
                AurorionConfigs.fileName("outro_mod", ModConfig.Type.SERVER, null));
    }

    @Test void blankVariantIsTreatedAsAbsent() {
        assertEquals(AurorionConfigs.fileName("aurorion_talk", ModConfig.Type.CLIENT, null),
                AurorionConfigs.fileName("aurorion_talk", ModConfig.Type.CLIENT, "  "));
    }

    /** Tudo dentro da pasta: se um mod escapar, a convencao vira mentira e ninguem nota. */
    @Test void everythingLandsInsideTheFolder() {
        for (ModConfig.Type type : ModConfig.Type.values()) {
            String path = AurorionConfigs.fileName("aurorion_portais", type, null);
            assertTrue(path.startsWith(AurorionConfigs.FOLDER + "/"), path);
        }
    }
}
