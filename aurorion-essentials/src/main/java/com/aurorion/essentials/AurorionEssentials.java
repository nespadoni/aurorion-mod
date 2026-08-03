package com.aurorion.essentials;

import com.aurorion.essentials.cleanup.CleanupConfig;
import com.mojang.logging.LogUtils;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

/**
 * Ponto de entrada. Comandos, rede e eventos se registram sozinhos via
 * {@code @EventBusSubscriber(modid = MOD_ID)} — so a config precisa de registro manual.
 */
@Mod(AurorionEssentials.MOD_ID)
public class AurorionEssentials {
    public static final String MOD_ID = "aurorion_essentials";
    public static final String MOD_NAME = "Aurorion Essentials";

    public static final Logger LOGGER = LogUtils.getLogger();

    public AurorionEssentials(ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, CleanupConfig.SPEC);
    }
}
