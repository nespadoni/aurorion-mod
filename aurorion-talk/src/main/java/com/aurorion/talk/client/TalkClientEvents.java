package com.aurorion.talk.client;

import com.aurorion.talk.AurorionTalk;
import com.aurorion.talk.client.gui.BalloonCustomizationScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

public final class TalkClientEvents {
    public static final KeyMapping OPEN_CONFIG = new KeyMapping(
            "key.aurorion_talk.open_config",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_B,
            "key.categories.aurorion_talk"
    );

    private TalkClientEvents() {
    }

    @EventBusSubscriber(modid = AurorionTalk.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModBus {
        private ModBus() {
        }

        @SubscribeEvent
        public static void registerKeys(RegisterKeyMappingsEvent event) {
            event.register(OPEN_CONFIG);
        }

        /** Catalogo de skins/enfeites recarrega junto com o resto (F3+T ou troca de resource pack). */
        @SubscribeEvent
        public static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener(new BalloonCatalog());
        }
    }

    @EventBusSubscriber(modid = AurorionTalk.MOD_ID, value = Dist.CLIENT)
    public static final class GameBus {
        private GameBus() {
        }

        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            Minecraft minecraft = Minecraft.getInstance();
            while (OPEN_CONFIG.consumeClick()) {
                minecraft.setScreen(new BalloonCustomizationScreen());
            }
        }

        /** Sem isso o cache de estilos vaza de um servidor para o outro. */
        @SubscribeEvent
        public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
            ClientStyles.clear();
        }
    }
}
