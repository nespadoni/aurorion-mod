package com.aurorion.economia;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Ponto de entrada. Comandos e eventos se registram sozinhos via
 * {@code @EventBusSubscriber(modid = MOD_ID)}; este mod nao tem config em arquivo de proposito —
 * quanto dinheiro existe e decisao de staff por comando, nao de TOML (ver ECONOMIA.md §4.1).
 */
@Mod(AurorionEconomia.MOD_ID)
public class AurorionEconomia {
    public static final String MOD_ID = "aurorion_economia";
    public static final String MOD_NAME = "Aurorion Economia";

    public static final Logger LOGGER = LogUtils.getLogger();
}
