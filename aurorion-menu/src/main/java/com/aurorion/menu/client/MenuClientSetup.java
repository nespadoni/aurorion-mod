package com.aurorion.menu.client;

import com.aurorion.core.client.menu.CharacterMenu;
import com.aurorion.menu.AurorionMenu;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

/** Registros de cliente: o host do menu no core, a seção Interações, a tecla e a camada do painel. */
@EventBusSubscriber(modid = AurorionMenu.MOD_ID, value = Dist.CLIENT)
public final class MenuClientSetup {
    private MenuClientSetup() {
    }

    @SubscribeEvent
    public static void onSetup(FMLClientSetupEvent event) {
        CharacterMenu.provide(MenuHost.INSTANCE);
        CharacterMenu.register(new InteractionsSection());
    }

    @SubscribeEvent
    public static void onKeys(RegisterKeyMappingsEvent event) {
        event.register(MenuKeys.OPEN);
    }

    /** Não desenha no HUD: pinta o menu na textura do painel que o {@link MenuSpace} pendura no mundo. */
    @SubscribeEvent
    public static void onLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(MenuSpace.LAYER, new MenuSpace.CanvasLayer());
    }
}
