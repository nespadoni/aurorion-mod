package com.aurorion.economia;

import com.mojang.logging.LogUtils;
import com.aurorion.core.config.AurorionConfigs;
import com.aurorion.economia.config.EconomyConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Ponto de entrada. Comandos e eventos se registram sozinhos via
 * {@code @EventBusSubscriber(modid = MOD_ID)}. Config define preços, nunca emite moeda.
 */
@Mod(AurorionEconomia.MOD_ID)
public class AurorionEconomia {
    public static final String MOD_ID = "aurorion_economia";
    public static final String MOD_NAME = "Aurorion Economia";

    public static final Logger LOGGER = LogUtils.getLogger();

    public AurorionEconomia(ModContainer container) {
        AurorionConfigs.register(container, ModConfig.Type.SERVER, EconomyConfig.SPEC);
    }
}
