package com.aurorion.magia.client;

import com.aurorion.core.client.ShaderPacks;
import com.aurorion.magia.AurorionMagia;
import com.aurorion.magia.config.MagiaClientConfig;
import com.aurorion.magia.registry.MagiaEffects;
import com.aurorion.magia.registry.MagiaEntities;
import com.mojang.blaze3d.shaders.FogShape;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.util.TriState;
import org.jetbrains.annotations.Nullable;

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
            // A nevoa vai ABAIXO de tudo: ela e cenario, e nao pode cobrir barra de itens nem chat.
            // A possessao vai acima, porque ali o ponto e justamente cobrir a tela inteira.
            event.registerBelow(VanillaGuiLayers.CAMERA_OVERLAYS, FogLayer.ID, new FogLayer());
            event.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS, PossessionLayer.ID, new PossessionLayer());
        }

        @SubscribeEvent
        public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(MagiaEntities.SPELL_ZONE.get(), SpellZoneRenderer::new);
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
            Minecraft minecraft = Minecraft.getInstance();
            LocalPlayer player = minecraft.player;
            if (player == null || minecraft.level == null || minecraft.isPaused()) return;
            forceGaze(player);
            MagiaSoundscape.tick(player);
        }

        /**
         * Aspectus Captus: o corpo e a cabeca do cativo viram para quem o prende, todo tick. A
         * rotacao sai para o servidor pelo movimento normal, e os outros veem a cabeca virar. Entre
         * um tick e outro, o mouse ainda mexe — a camera e corrigida quadro a quadro em
         * {@link #onCameraAngles}.
         */
        private static void forceGaze(LocalPlayer player) {
            float[] look = gazeAngles(player, 1.0f);
            if (look == null) return;
            player.setYRot(Mth.rotLerp(.65F, player.getYRot(), look[0]));
            player.setXRot(Mth.lerp(.65F, player.getXRot(), look[1]));
            player.setYHeadRot(player.getYRot());
        }

        /** {yaw, pitch} dos olhos do jogador ate os do captor, ou {@code null} sem Aspectus. */
        @Nullable
        private static float[] gazeAngles(LocalPlayer player, float partial) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level == null) return null;
            Entity captor = ClientSpellVisuals.captorOf(minecraft.level, player.getId());
            if (captor == null || !player.hasEffect(MagiaEffects.CAPTIVE)) return null;
            Vec3 delta = captor.getEyePosition(partial).subtract(player.getEyePosition(partial));
            double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
            float yaw = (float) (Mth.atan2(delta.z, delta.x) * Mth.RAD_TO_DEG) - 90f;
            float pitch = (float) -(Mth.atan2(delta.y, horizontal) * Mth.RAD_TO_DEG);
            return new float[]{yaw, pitch};
        }

        /**
         * Campo de visao: o olhar preso aperta (zoom no captor), o Imperium respira, a dor pulsa com o
         * coracao, e o ajoelhado ve o mundo um pouco mais fechado. Respeita "Efeitos de distorcao".
         */
        @SubscribeEvent
        public static void onFov(ViewportEvent.ComputeFov event) {
            if (!event.usedConfiguredFov()) return;
            Minecraft minecraft = Minecraft.getInstance();
            LocalPlayer player = minecraft.player;
            if (player == null) return;
            double scale = minecraft.options.screenEffectScale().get();
            double t = player.tickCount + event.getPartialTick();
            double factor = 1;
            if (player.hasEffect(MagiaEffects.CAPTIVE)) factor *= 1 - .25 * scale;
            if (player.hasEffect(MagiaEffects.DISORIENTED)) factor *= 1 + Math.sin(t * .12) * .07 * scale;
            if (player.hasEffect(MagiaEffects.CRUCIATUS)) {
                factor *= 1 - Math.pow(Math.max(0, Math.sin(t * Math.PI * 2 / 10)), 6) * .06 * scale;
            }
            if (player.hasEffect(MagiaEffects.KNEELING)) factor *= 1 - .08 * scale;
            if (factor != 1) event.setFOV(event.getFOV() * factor);
        }

        /** Selos, aneis e correntes. Sai na primeira linha quando nao ha visual ativo. */
        @SubscribeEvent
        public static void onRenderLevel(RenderLevelStageEvent event) {
            SigilRenderer.render(event);
        }

        /**
         * Mundus Vacuus: o mundo esvazia. Enquanto o efeito durar, este cliente para de desenhar
         * qualquer vivo que nao seja o proprio jogador — pessoas, criaturas, montarias, manequins.
         *
         * <p>Ninguem some de verdade: o servidor nunca deixa de saber quem esta onde, o dano continua
         * chegando, o som continua tocando e para todos os outros a cena e normal. O que muda e so o
         * que este par de olhos ve. E por isso que a magia e uma tortura e nao uma vantagem: a pessoa
         * apanha de um corredor vazio.
         *
         * <p>Custo: uma consulta de efeito por entidade desenhada, e so enquanto alguem esta sob a
         * magia — {@code hasEffect} e uma busca em mapa pequeno, que o vanilla ja faz varias vezes por
         * quadro para cada corpo.
         */
        @SubscribeEvent
        public static void onRenderLiving(RenderLivingEvent.Pre<?, ?> event) {
            if (alone(event.getEntity())) event.setCanceled(true);
        }

        /** O nome flutuante sai junto: um nome pairando sozinho entregaria quem esta ali. */
        @SubscribeEvent
        public static void onRenderNameTag(RenderNameTagEvent event) {
            if (alone(event.getEntity())) event.setCanRender(TriState.FALSE);
        }

        /** {@code true} se este cliente esta sob o Mundo Vazio e {@code entity} nao e ele mesmo. */
        private static boolean alone(Entity entity) {
            LocalPlayer player = Minecraft.getInstance().player;
            return player != null && entity != player && player.hasEffect(MagiaEffects.SOLITARY);
        }

        /**
         * A neblina de verdade, do pipeline do vanilla: a zona do Devorar Luz e a aura de terror
         * fechando o ar em volta de quem esta dentro. Respeita neblina ja mais densa (agua, lava,
         * cegueira, a floresta do {@code aurorion-areas}).
         *
         * <p><b>So sem shader.</b> Com um pacote carregado no Iris, quem calcula a neblina e o shader
         * e este evento nao chega a lugar nenhum; ai quem desenha e a {@link FogLayer}, em espaco de
         * tela. As duas nunca rodam juntas — somadas, escureceriam o dobro.
         *
         * <p>A Presenca Aterradora fecha o ar tanto quanto o Devorar Luz: colado em quem a carrega, o
         * alcance de visao e {@value #DARK_FOG_DISTANCE} blocos. A escuridao do chao e das paredes nao
         * vem daqui — vem da Escuridao do vanilla que o servidor poe em quem esta dentro da aura.
         */
        @SubscribeEvent
        public static void onRenderFog(ViewportEvent.RenderFog event) {
            if (event.getCamera().getFluidInCamera() != FogType.NONE
                    || event.getMode() != FogRenderer.FogMode.FOG_TERRAIN || ShaderPacks.inUse()) return;
            Vec3 camera = event.getCamera().getPosition();
            float weight = Math.max(ClientSpellVisuals.darkness(camera), terrorWeight(camera));
            if (weight < .001F) return;
            float far = event.getFarPlaneDistance();
            float distance = far + (Math.min(far, DARK_FOG_DISTANCE) - far) * weight;
            event.setFarPlaneDistance(distance);
            event.setNearPlaneDistance(Math.min(event.getNearPlaneDistance(), distance * .1F));
            event.setFogShape(FogShape.SPHERE);
            event.setCanceled(true);
        }

        /**
         * A cor do ar. Sao dois ares diferentes: o do Devorar Luz guarda um roxo de vazio, e o do medo
         * nao guarda nada — preto e tudo o que a Presenca Aterradora tem a dizer.
         */
        @SubscribeEvent
        public static void onFogColor(ViewportEvent.ComputeFogColor event) {
            if (event.getCamera().getFluidInCamera() != FogType.NONE || ShaderPacks.inUse()) return;
            Vec3 camera = event.getCamera().getPosition();
            float terror = terrorWeight(camera);
            float darkness = ClientSpellVisuals.darkness(camera) * .9F;
            float weight = Math.max(terror, darkness);
            if (weight < .001F) return;
            // O roxo so aparece na parte da escuridao que o medo nao cobriu.
            float tint = Math.max(0, darkness - terror);
            event.setRed(event.getRed() * (1 - weight) + .02F * tint);
            event.setGreen(event.getGreen() * (1 - weight));
            event.setBlue(event.getBlue() * (1 - weight) + .04F * tint);
        }

        /**
         * Quanto a aura de terror fecha o ar na camera: 0 para quem nao esta apavorado (o dono da aura
         * enxerga a propria praça normalmente) e 1 colado em quem a carrega.
         */
        static float terrorWeight(Vec3 camera) {
            Minecraft minecraft = Minecraft.getInstance();
            LocalPlayer player = minecraft.player;
            if (player == null || minecraft.level == null || !player.hasEffect(MagiaEffects.TERRIFIED)) return 0;
            return ClientSpellVisuals.terror(minecraft.level, camera);
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
            if (player == null) return;
            double t = player.tickCount + event.getPartialTick();
            double distortion = minecraft.options.screenEffectScale().get();

            // Olhar preso: a camera encara o captor quadro a quadro, sem o tranco do tick.
            float[] look = gazeAngles(player, (float) event.getPartialTick());
            if (look != null) {
                event.setYaw(look[0]);
                event.setPitch(look[1]);
            }

            // Imperium: a cabeca balanca devagar, como se outra pessoa a movesse.
            if (player.hasEffect(MagiaEffects.DISORIENTED) && distortion > 0) {
                event.setRoll((float) (event.getRoll() + Math.sin(t * .09) * 4 * distortion));
                event.setYaw((float) (event.getYaw() + Math.sin(t * .05) * 1.5 * distortion));
            }

            // Medo: um tremor pequeno e no ritmo do coracao (100 bpm = 12 ticks), nao o espasmo da
            // dor. Quem esta apavorado ainda joga; a mao e que nao para quieta.
            if (distortion > 0) {
                float closeness = terrorWeight(event.getCamera().getPosition());
                double shake = MagiaClientConfig.CAMERA_SHAKE.get() * distortion * closeness;
                if (shake > 0) {
                    double beat = Math.pow(Math.max(0, Math.sin(t * Math.PI * 2 / 12)), 6);
                    event.setRoll((float) (event.getRoll() + Math.sin(t * 1.7) * 0.8 * shake * (0.4 + beat)));
                    event.setPitch((float) (event.getPitch() + Math.cos(t * 2.3) * 0.5 * shake * (0.4 + beat)));
                }
            }

            if (!player.hasEffect(MagiaEffects.CRUCIATUS)) return;
            double intensity = MagiaClientConfig.CAMERA_SHAKE.get() * distortion;
            if (intensity <= 0) return;

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
            MagiaSoundscape.clear();
            ClientSpellVisuals.clear();
            FogLayer.clear();
        }
    }
}
