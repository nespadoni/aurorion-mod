package com.aurorion.mundos;

import com.aurorion.core.config.AurorionConfigs;
import com.aurorion.mundos.config.MundosConfig;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

/**
 * Mais de um overworld.
 *
 * <p>As dimensoes nao estao em codigo: cada uma e um JSON em
 * {@code data/<namespace>/dimension/}, apontando para o mesmo gerador e o mesmo preset de biomas do
 * {@code minecraft:overworld}. E isso que faz os mods de worldgen do modpack valerem nelas <b>sem
 * uma linha de integracao por mod</b> — eles injetam no preset, e o preset e o mesmo.
 *
 * <p>O que este mod adiciona em cima disso sao as tres coisas que o vanilla amarra ao overworld:
 *
 * <ul>
 *   <li><b>Seed</b>: {@code ServerLevel#getSeed()} devolve o seed do mundo para toda dimensao, entao
 *       dois overworlds com o mesmo gerador gerariam o mesmo mapa.</li>
 *   <li><b>Barreira</b>: {@code MinecraftServer#createLevels} pendura a barreira do overworld em toda
 *       outra dimensao, e {@code PlayerList#sendLevelInfo} manda sempre a do overworld ao cliente.</li>
 *   <li><b>Destino do portal</b>: {@code NetherPortalBlock#getPortalDestination} so sabe ir e voltar
 *       do Nether.</li>
 * </ul>
 *
 * <p>Nao ha {@code DeferredRegister} aqui: este mod nao registra item, bloco nem entidade.
 *
 * <h2>O que este mod nao faz</h2>
 *
 * <p>Ele decide <b>para onde</b> um portal leva. Quem decide <b>quando</b> se pode atravessar e o
 * {@code aurorion-portais}, e os dois nao se conhecem — nao ha import nem dependencia declarada entre
 * eles (SDD §9.1). Uma dimensao daqui nasce trancada la pela regra de negar-por-padrao, sem nenhum
 * codigo de ligacao.
 */
@Mod(AurorionMundos.MOD_ID)
public class AurorionMundos {
    public static final String MOD_ID = "aurorion_mundos";
    public static final String MOD_NAME = "Aurorion Mundos";

    public static final Logger LOGGER = LogUtils.getLogger();

    public AurorionMundos(IEventBus modEventBus, ModContainer container) {
        AurorionConfigs.register(container, ModConfig.Type.SERVER, MundosConfig.SPEC);
    }
}
