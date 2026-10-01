package com.aurorion.integracao;

import com.aurorion.core.config.AurorionConfigs;
import com.aurorion.core.integration.GameFacts;
import com.aurorion.integracao.bridge.FactBridge;
import com.aurorion.integracao.config.IntegracaoConfig;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

/**
 * A ponte entre o jogo e o site: mortes, quedas no Limbo, portais e casas viram acontecimentos na
 * linha do tempo, enviados no instante em que acontecem.
 *
 * <p>So o servidor precisa deste mod. Ele nao registra item, bloco, pacote nem tela; os outros mods
 * publicam pelo {@link GameFacts} do core sem saber que ele existe, e sem ele o jogo continua igual.
 * Rede e disco moram numa thread propria (ver {@code outbox.Outbox}): a thread do servidor so
 * enfileira.
 */
@Mod(AurorionIntegracao.MOD_ID)
public final class AurorionIntegracao {
    public static final String MOD_ID = "aurorion_integracao";
    public static final Logger LOGGER = LogUtils.getLogger();

    public AurorionIntegracao(IEventBus modEventBus, ModContainer container) {
        // STARTUP, e nao SERVER: config SERVER e enviada a todo cliente, e o token iria junto.
        AurorionConfigs.register(container, ModConfig.Type.STARTUP, IntegracaoConfig.SPEC);
        GameFacts.provide(FactBridge::accept);
    }
}
