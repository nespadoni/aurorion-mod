package com.aurorion.menu;

import com.aurorion.core.config.AurorionConfigs;
import com.aurorion.menu.config.MenuClientConfig;
import com.aurorion.menu.config.MenuConfig;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

/**
 * O menu do personagem em estilo de MMO.
 *
 * <ul>
 *   <li>{@code client}: o painel do menu flutuando no mundo na frente da pessoa (as seções vêm do
 *   {@code CharacterMenu} do core, que cada mod alimenta), o gesto de abrir, o painel borrado e a pose do
 *   braço que os outros veem.</li>
 *   <li>{@code server}: quem está com o menu aberto ou meditando (repassado a quem rastreia a pessoa) e a
 *   meditação.</li>
 *   <li>{@code network}: dois pacotes pequenos, só quando o estado muda.</li>
 * </ul>
 */
@Mod(AurorionMenu.MOD_ID)
public final class AurorionMenu {
    public static final String MOD_ID = "aurorion_menu";
    public static final Logger LOGGER = LogUtils.getLogger();
    /** O emote da meditação, tocado pelo Emotecraft quando ele está no pack. */
    public static final String MEDITATION_EMOTE = "/assets/aurorion_menu/emotes/meditar.emotecraft";

    public AurorionMenu(IEventBus modBus, ModContainer container) {
        AurorionConfigs.register(container, ModConfig.Type.SERVER, MenuConfig.SPEC);
        AurorionConfigs.register(container, ModConfig.Type.CLIENT, MenuClientConfig.SPEC);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
