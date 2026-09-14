package com.aurorion.ethereal;

import com.aurorion.core.config.AurorionConfigs;
import com.aurorion.ethereal.config.EtherealConfig;
import com.aurorion.ethereal.registry.EtherealBlockEntities;
import com.aurorion.ethereal.registry.EtherealBlocks;
import com.aurorion.ethereal.registry.EtherealCreativeTab;
import com.aurorion.ethereal.registry.EtherealItems;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

/**
 * O mod central de Ethereal: casas, Cerimonia de Vinculacao e placares num jar so.
 *
 * <p>Os tres sistemas moram juntos porque sao <b>o mesmo dado visto de tres angulos</b>. A casa de
 * um jogador decide o que a cerimonia grava; os pontos de cada jogador com casa somam no total da
 * casa; o placar exibe essa soma. Separar isso em mods diferentes exigiria ou uma API entre eles ou
 * uma copia do registro de casas dos dois lados — e o placar antigo pagava exatamente esse preco:
 * ele guardava "casa" como texto livre digitado na GUI, sem nenhuma ligacao com a casa de verdade
 * do jogador.
 *
 * <p>Continua valendo a regra do ecossistema (SDD §3): este mod nao depende de nenhum outro mod
 * Aurorion alem do {@code aurorion-core}. O Altar de Selecao entra pela tag
 * {@code aurorion_ethereal:house_altars}, nunca por import.
 *
 * <p>Quase tudo se registra sozinho via {@code @EventBusSubscriber(modid = MOD_ID)}; so os
 * {@code DeferredRegister} e a config precisam de registro manual.
 */
@Mod(AurorionEthereal.MOD_ID)
public final class AurorionEthereal {
    public static final String MOD_ID = "aurorion_ethereal";
    public static final String MOD_NAME = "Aurorion Ethereal";

    public static final Logger LOGGER = LogUtils.getLogger();

    public AurorionEthereal(IEventBus modBus, ModContainer container) {
        EtherealBlocks.BLOCKS.register(modBus);
        EtherealItems.ITEMS.register(modBus);
        EtherealBlockEntities.BLOCK_ENTITIES.register(modBus);
        EtherealCreativeTab.TABS.register(modBus);

        AurorionConfigs.register(container, ModConfig.Type.SERVER, EtherealConfig.SPEC);
    }
}
