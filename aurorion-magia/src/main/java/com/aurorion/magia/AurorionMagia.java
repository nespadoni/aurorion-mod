package com.aurorion.magia;

import com.aurorion.core.config.AurorionConfigs;
import com.aurorion.magia.config.MagiaClientConfig;
import com.aurorion.magia.config.MagiaConfig;
import com.aurorion.magia.registry.MagiaComponents;
import com.aurorion.magia.registry.MagiaCreativeTabs;
import com.aurorion.magia.registry.MagiaEffects;
import com.aurorion.magia.registry.MagiaEntities;
import com.aurorion.magia.registry.MagiaItems;
import com.aurorion.magia.registry.MagiaSpells;
import com.aurorion.magia.registry.MagiaSounds;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

/**
 * Addon do Iron's Spells para o Aurorion: magia se aprende em aula, nao se acha no chao.
 *
 * <p>Tres pecas, cada uma no seu pacote:
 *
 * <ul>
 *   <li>{@code unlock}: quem pode conjurar o que. Liberacao por magia ou por escola inteira, gravada
 *       por personagem e espelhada no Iron's Restrictions.</li>
 *   <li>{@code spell} + {@code effect}: as magias autorais. A regra roda no servidor; o que e so
 *       visual e desenhado pelo cliente a partir do efeito ja sincronizado.</li>
 *   <li>{@code passive}: o que fica no personagem em vez de ser conjurado. Um pergaminho lido uma
 *       vez deixa uma marca — curar no toque, exalar medo — que nao custa mana, nao entra em
 *       recarga e nao se desequipa.</li>
 *   <li>{@code client}: particulas, tremor de camera, controles invertidos e escurecimento. Nada ali
 *       gera pacote nem roda no servidor.</li>
 * </ul>
 */
@Mod(AurorionMagia.MOD_ID)
public class AurorionMagia {
    public static final String MOD_ID = "aurorion_magia";
    public static final String MOD_NAME = "Aurorion Magia";

    public static final Logger LOGGER = LogUtils.getLogger();

    public AurorionMagia(IEventBus modEventBus, ModContainer container) {
        MagiaSpells.SPELLS.register(modEventBus);
        MagiaSounds.SOUNDS.register(modEventBus);
        MagiaEffects.EFFECTS.register(modEventBus);
        MagiaEntities.ENTITIES.register(modEventBus);
        MagiaComponents.COMPONENTS.register(modEventBus);
        MagiaItems.ITEMS.register(modEventBus);
        MagiaCreativeTabs.TABS.register(modEventBus);

        AurorionConfigs.register(container, ModConfig.Type.SERVER, MagiaConfig.SPEC);
        AurorionConfigs.register(container, ModConfig.Type.CLIENT, MagiaClientConfig.SPEC);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
