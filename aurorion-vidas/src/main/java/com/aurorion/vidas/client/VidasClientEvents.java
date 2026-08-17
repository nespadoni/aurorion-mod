package com.aurorion.vidas.client;

import com.aurorion.vidas.AurorionVidas;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * Registro do HUD e limpeza do estado local. Mesma separacao em dois bus do
 * {@code aurorion-talk}: o de mod para registrar, o de jogo para reagir.
 */
public final class VidasClientEvents {
    private VidasClientEvents() {
    }

    @EventBusSubscriber(modid = AurorionVidas.MOD_ID, value = Dist.CLIENT)
    public static final class ModBus {
        private ModBus() {
        }

        @SubscribeEvent
        public static void registerLayers(RegisterGuiLayersEvent event) {
            event.registerAbove(VanillaGuiLayers.FOOD_LEVEL, LivesHudLayer.ID, new LivesHudLayer());
        }
    }

    @EventBusSubscriber(modid = AurorionVidas.MOD_ID, value = Dist.CLIENT)
    public static final class GameBus {
        private GameBus() {
        }

        /** Sem isto o contador do servidor anterior apareceria no proximo, ate o primeiro pacote chegar. */
        @SubscribeEvent
        public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
            ClientLives.clear();
        }
    }
}
