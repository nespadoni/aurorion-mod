package com.aurorion.magia.client;

import com.aurorion.magia.AurorionMagia;
import com.aurorion.magia.config.MagiaClientConfig;
import com.aurorion.magia.registry.MagiaEffects;
import com.mojang.blaze3d.shaders.FogShape;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.world.level.material.FogType;
import net.neoforged.fml.ModList;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
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
        /** Alcance da neblina no centro da zona do Lux Vorata, em blocos. */
        private static final float DARK_FOG_DISTANCE = 5F;
        private static final boolean EMOTECRAFT = ModList.get().isLoaded("emotecraft");

        private GameBus() {
        }

        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            ClientSpellVisuals.tick();
        }

        /** Selos, aneis e correntes. Sai na primeira linha quando nao ha visual ativo. */
        @SubscribeEvent
        public static void onRenderLevel(RenderLevelStageEvent event) {
            SigilRenderer.render(event);
        }

        /**
         * Lux Vorata: dentro da zona, a neblina fecha em volta de quem esta la. Respeita neblina ja
         * mais densa (agua, lava, cegueira, a floresta do {@code aurorion-areas}).
         */
        @SubscribeEvent
        public static void onRenderFog(ViewportEvent.RenderFog event) {
            if (event.getCamera().getFluidInCamera() != FogType.NONE
                    || event.getMode() != FogRenderer.FogMode.FOG_TERRAIN) return;
            float weight = ClientSpellVisuals.darkness(event.getCamera().getPosition());
            if (weight < .001F) return;
            float far = event.getFarPlaneDistance();
            float distance = far + (Math.min(far, DARK_FOG_DISTANCE) - far) * weight;
            event.setFarPlaneDistance(distance);
            event.setNearPlaneDistance(Math.min(event.getNearPlaneDistance(), distance * .1F));
            event.setFogShape(FogShape.SPHERE);
            event.setCanceled(true);
        }

        @SubscribeEvent
        public static void onFogColor(ViewportEvent.ComputeFogColor event) {
            if (event.getCamera().getFluidInCamera() != FogType.NONE) return;
            float weight = ClientSpellVisuals.darkness(event.getCamera().getPosition()) * .9F;
            if (weight < .001F) return;
            event.setRed(event.getRed() * (1 - weight) + .02F * weight);
            event.setGreen(event.getGreen() * (1 - weight));
            event.setBlue(event.getBlue() * (1 - weight) + .04F * weight);
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
            Input input = event.getInput();
            // Genua Flecte sem Emotecraft: o ajoelhado fica agachado, e todos veem.
            if (!EMOTECRAFT && event.getEntity().hasEffect(MagiaEffects.KNEELING)) input.shiftKeyDown = true;
            if (!event.getEntity().hasEffect(MagiaEffects.DISORIENTED)) return;

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
