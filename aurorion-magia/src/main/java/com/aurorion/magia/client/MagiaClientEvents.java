package com.aurorion.magia.client;

import com.aurorion.magia.AurorionMagia;
import com.aurorion.magia.config.MagiaClientConfig;
import com.aurorion.magia.registry.MagiaEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * Tudo que as magias fazem na tela do jogador. Nenhuma destas reacoes depende de pacote nosso: o
 * cliente olha os efeitos do proprio jogador, que o vanilla ja sincroniza.
 *
 * <p>Mesma separacao em dois bus do {@code aurorion-vidas}: o de mod para registrar, o de jogo para
 * reagir.
 */
public final class MagiaClientEvents {
    private MagiaClientEvents() {
    }

    @EventBusSubscriber(modid = AurorionMagia.MOD_ID, value = Dist.CLIENT)
    public static final class ModBus {
        private ModBus() {
        }

        @SubscribeEvent
        public static void registerLayers(RegisterGuiLayersEvent event) {
            event.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS, DisorientationLayer.ID, new DisorientationLayer());
        }
    }

    @EventBusSubscriber(modid = AurorionMagia.MOD_ID, value = Dist.CLIENT)
    public static final class GameBus {
        private GameBus() {
        }

        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            ClientSpellVisuals.tick();
        }

        /**
         * Tremor de dor do Cruciatus. Tres senoides de frequencias diferentes somadas: irregular o
         * bastante para parecer espasmo, suave o bastante para nao dar enjoo. Respeita a opcao
         * "Efeitos de distorcao" do vanilla, que existe justamente para quem passa mal com isso.
         */
        @SubscribeEvent
        public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
            Minecraft minecraft = Minecraft.getInstance();
            LocalPlayer player = minecraft.player;
            if (player == null || !player.hasEffect(MagiaEffects.CRUCIATUS)) return;

            double intensity = MagiaClientConfig.CAMERA_SHAKE.get() * minecraft.options.screenEffectScale().get();
            if (intensity <= 0) return;

            double t = player.tickCount + event.getPartialTick();
            event.setRoll((float) (event.getRoll() + (Math.sin(t * 2.9) * 2.2 + Math.sin(t * 7.3) * 0.9) * intensity));
            event.setYaw((float) (event.getYaw() + Math.sin(t * 3.7) * 0.7 * intensity));
            event.setPitch((float) (event.getPitch() + Math.cos(t * 4.3) * 0.7 * intensity));
        }

        /**
         * Controles invertidos do Imperium. Movimento no Minecraft e decidido pelo cliente, entao e
         * aqui que a inversao precisa acontecer; o servidor decide quando comeca e quando acaba.
         */
        @SubscribeEvent
        public static void onMovementInput(MovementInputUpdateEvent event) {
            if (!event.getEntity().hasEffect(MagiaEffects.DISORIENTED)) return;

            Input input = event.getInput();
            input.forwardImpulse = -input.forwardImpulse;
            input.leftImpulse = -input.leftImpulse;
            boolean up = input.up;
            input.up = input.down;
            input.down = up;
            boolean left = input.left;
            input.left = input.right;
            input.right = left;
        }

        @SubscribeEvent
        public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
            ClientSpellVisuals.clear();
        }
    }
}
