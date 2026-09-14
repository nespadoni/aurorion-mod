package com.aurorion.aeonita;

import com.aurorion.core.config.AurorionConfigs;
import com.aurorion.aeonita.config.AeonitaClientConfig;
import com.aurorion.aeonita.registry.AeonitaBlocks;
import com.aurorion.aeonita.registry.AeonitaCreativeTab;
import com.aurorion.aeonita.registry.AeonitaItems;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

/**
 * Mod de conteudo do ecossistema: itens e blocos reutilizaveis, sem mecanica de ato nenhum.
 * Quem da comportamento de historia a esse conteudo e outro mod (ex.: {@code aurorion_ato2} usa o
 * Altar de Selecao como entrada da escolha de casa) — aqui so existe o conteudo em si.
 *
 * <p>A ordem de registro importa: os itens de bloco referenciam os {@code DeferredBlock}, entao
 * {@link AeonitaBlocks} precisa estar carregado antes de {@link AeonitaItems}.
 */
@Mod(AurorionAeonita.MOD_ID)
public class AurorionAeonita {
    public static final String MOD_ID = "aurorion_aeonita";
    public static final String MOD_NAME = "Aurorion Aeonita";

    public static final Logger LOGGER = LogUtils.getLogger();

    public AurorionAeonita(IEventBus modEventBus, ModContainer container) {
        AeonitaBlocks.BLOCKS.register(modEventBus);
        AeonitaItems.ITEMS.register(modEventBus);
        AeonitaCreativeTab.CREATIVE_MODE_TABS.register(modEventBus);

        AurorionConfigs.register(container, ModConfig.Type.CLIENT, AeonitaClientConfig.SPEC);
    }
}
