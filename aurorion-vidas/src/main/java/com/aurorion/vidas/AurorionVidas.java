package com.aurorion.vidas;

import com.aurorion.core.config.AurorionConfigs;
import com.aurorion.vidas.config.LivesClientConfig;
import com.aurorion.vidas.config.LivesConfig;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

/**
 * Sistema de vidas: cada morte gasta uma, e zerar significa exilio.
 *
 * <p>O exilio depende de o destino ser inescapavel para valer alguma coisa — e quem garante isso e
 * o {@code aurorion_portais}, que mantem o Nether trancado. Mesmo assim <b>nao ha dependencia
 * declarada nem import entre os dois</b>: eles se coordenam por eventos do vanilla.
 *
 * <ul>
 *   <li>A ida usa {@code PlayerRespawnPositionEvent}, que <em>nao</em> passa por
 *       {@code changeDimension} — o portao do outro mod nem chega a ser consultado.</li>
 *   <li>A volta e vetada por um listener proprio de {@code EntityTravelToDimensionEvent}. Os dois
 *       mods cancelam o mesmo evento, cada um pelo seu motivo, sem saber um do outro.</li>
 * </ul>
 *
 * <p>Sem o {@code aurorion_portais} instalado o exilio continua funcionando — so fica mais fraco,
 * porque o exilado consegue sair pelo portal do Nether se ninguem estiver trancando a dimensao.
 */
@Mod(AurorionVidas.MOD_ID)
public class AurorionVidas {
    public static final String MOD_ID = "aurorion_vidas";
    public static final String MOD_NAME = "Aurorion Vidas";

    public static final Logger LOGGER = LogUtils.getLogger();

    public AurorionVidas(IEventBus modEventBus, ModContainer container) {
        AurorionConfigs.register(container, ModConfig.Type.SERVER, LivesConfig.SPEC);
        AurorionConfigs.register(container, ModConfig.Type.CLIENT, LivesClientConfig.SPEC);
    }
}
