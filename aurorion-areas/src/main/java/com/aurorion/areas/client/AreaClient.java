package com.aurorion.areas.client;

import com.aurorion.areas.AurorionAreas;
import com.aurorion.areas.network.AreaStatePayload;
import com.aurorion.core.client.ScreenFog;
import com.aurorion.core.client.ShaderPacks;
import com.mojang.blaze3d.shaders.FogShape;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

@EventBusSubscriber(modid = AurorionAreas.MOD_ID, value = Dist.CLIENT)
public final class AreaClient {
    private static ResourceLocation dimension;
    private static float targetFog, lastFogDistance, fogWeight, vignette, pulseStrength;
    /** Apagao: o que esta na tela agora e o alvo que o servidor mandou para este pulso. */
    private static float blackout, blackoutStrength;
    private static int color, pulseRemaining, blackoutRemaining;
    private static boolean flightBlocked;
    private AreaClient() {}
    public static void accept(AreaStatePayload payload) {
        dimension = payload.dimension();
        targetFog = Float.isFinite(payload.fogDistance()) ? Math.clamp(payload.fogDistance(), 0, 256) : 0;
        if (targetFog > 0) { lastFogDistance = targetFog; color = payload.fogColor(); }
        flightBlocked = payload.flightBlocked();
        pulseStrength = Float.isFinite(payload.vignette()) ? Math.clamp(payload.vignette(), 0, .9F) : 0;
        blackoutStrength = Float.isFinite(payload.blackout()) ? Math.clamp(payload.blackout(), 0, 1) : 0;
        pulseRemaining = Math.clamp(payload.pulseTicks(), 0, 600);
        blackoutRemaining = Math.clamp(payload.blackoutTicks(), 0, 600);
    }
    private static boolean current() {
        var level = Minecraft.getInstance().level;
        return level != null && dimension != null && dimension.equals(level.dimension().location());
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (mc.isPaused()) return;
        if (!current()) { fogWeight = 0; vignette = 0; blackout = 0; return; }
        fogWeight += ((targetFog > 0 ? 1 : 0) - fogWeight) * .08F;
        if (pulseRemaining > 0) pulseRemaining--;
        if (blackoutRemaining > 0) blackoutRemaining--;
        vignette += ((pulseRemaining > 0 ? pulseStrength : 0) - vignette) * .15F;
        // O apagao fecha mais devagar do que abre: cair na escuridao em dois segundos assusta,
        // voltar dela no mesmo tempo parece um piscar de olhos e desmancha o susto.
        float target = blackoutRemaining > 0 ? blackoutStrength : 0;
        blackout += (target - blackout) * (target > blackout ? .10F : .04F);
        // Avoid re-triggering local elytra animation while the server vetoes it.
        if (flightBlocked && mc.player != null) {
            if (mc.player.isFallFlying()) mc.player.stopFallFlying();
            mc.player.getAbilities().flying = false;
        }
        mist(mc);
    }

    /**
     * A neblina que se ve passar.
     *
     * <p>Neblina desenhada, seja pelo plano distante ou pela tela, e uma cor: ela nao tem volume, nao
     * se move e nao passa entre as arvores. Particula tem as tres coisas — e, ao contrario do
     * {@code RenderFog}, ela atravessa o pipeline do shader como qualquer particula do jogo, entao
     * este e o pedaco do ambiente que aparece igual com BSL, com Complementary e sem shader nenhum.
     *
     * <p>Sao poucas e perto: no maximo tres por tick, num cubo de oito blocos em volta da camera, e
     * divididas pela opcao "Particulas" do vanilla. Elas nao iluminam, nao colidem e morrem sozinhas.
     */
    private static void mist(Minecraft mc) {
        if (fogWeight < .15F || mc.level == null || mc.player == null) return;
        int stride = switch (mc.options.particles().get()) {
            case ALL -> 1;
            case DECREASED -> 2;
            case MINIMAL -> 4;
        };
        if (mc.level.getGameTime() % stride != 0) return;

        RandomSource random = mc.level.random;
        Vec3 eye = mc.player.getEyePosition();
        int count = Math.max(1, Math.round(3 * fogWeight) / stride);
        for (int i = 0; i < count; i++) {
            double x = eye.x + (random.nextDouble() - .5) * 16;
            double y = eye.y + (random.nextDouble() - .7) * 6;
            double z = eye.z + (random.nextDouble() - .5) * 16;
            mc.level.addParticle(ParticleTypes.LARGE_SMOKE, x, y, z,
                    (random.nextDouble() - .5) * .02, .004, (random.nextDouble() - .5) * .02);
            if (random.nextInt(3) == 0) {
                mc.level.addParticle(ParticleTypes.ASH, x, y + 1, z, 0, -.01, 0);
            }
        }
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        dimension = null; targetFog = 0; fogWeight = 0; vignette = 0; pulseRemaining = 0; flightBlocked = false;
        blackout = 0; blackoutStrength = 0; blackoutRemaining = 0;
    }
    /**
     * A neblina de verdade, do pipeline do vanilla.
     *
     * <p><b>So sem shader.</b> Com um pacote carregado no Iris, quem calcula a neblina e o fragment
     * shader do pacote e este evento nao e consultado — era por isso que a Floresta Negra existia
     * para quem joga sem shader e sumia para quem joga com BSL, Complementary ou Solas. Nesse caso
     * quem desenha e {@link #screenFog}, em espaco de tela, onde nenhum pacote interfere.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void fog(ViewportEvent.RenderFog event) {
        if (!current() || fogWeight < .001F || ShaderPacks.inUse()
                || event.getCamera().getFluidInCamera() != FogType.NONE
                || event.getMode() != FogRenderer.FogMode.FOG_TERRAIN) return;
        float far = event.getFarPlaneDistance();
        // Respect an already denser vanilla/mod fog, including blindness and lava/water.
        float requested = lastFogDistance;
        float distance = far + (Math.min(far, requested) - far) * fogWeight;
        event.setFarPlaneDistance(distance);
        event.setNearPlaneDistance(Math.min(event.getNearPlaneDistance(), distance * .15F));
        event.setFogShape(FogShape.SPHERE);
        event.setCanceled(true);
    }
    @SubscribeEvent public static void color(ViewportEvent.ComputeFogColor event) {
        if (!current() || fogWeight < .001F || ShaderPacks.inUse()
                || event.getCamera().getFluidInCamera() != FogType.NONE) return;
        float blend = fogWeight * .75F;
        event.setRed(event.getRed() + (((color >> 16 & 255) / 255F) - event.getRed()) * blend);
        event.setGreen(event.getGreen() + (((color >> 8 & 255) / 255F) - event.getGreen()) * blend);
        event.setBlue(event.getBlue() + (((color & 255) / 255F) - event.getBlue()) * blend);
    }
    /**
     * Sombra periferica e apagao, nesta ordem: o apagao vem por cima porque ele engole a sombra
     * quando chega perto de 1, e nao o contrario.
     *
     * <p>{@code hideGui} nao esconde nada disto de proposito — quem aperta F1 dentro da floresta nao
     * deveria ganhar visao noturna de brinde. O que ele faz e nao desenhar quando nao ha nada a
     * desenhar, que e o caso em 99% do tempo de jogo.
     */
    /**
     * A neblina quando o pipeline do vanilla nao serve: mesma densidade, mesma cor, pintada depois de
     * o quadro estar composto.
     *
     * <p>Desenhada <b>antes</b> da sombra periferica e do apagao de {@link #overlay}, na mesma ordem
     * em que o olho as leria no mundo: primeiro o ar, depois o que fecha em volta.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void screenFog(RenderGuiEvent.Post event) {
        if (!current() || fogWeight < .001F || lastFogDistance <= 0 || !ShaderPacks.inUse()) return;
        var camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        if (camera.getFluidInCamera() != FogType.NONE) return;
        // A mesma leitura da neblina de verdade: 26 blocos de alcance e um veu; 8, uma parede.
        float density = Math.clamp(1 - (lastFogDistance - 6F) / 50F, 0, 1);
        ScreenFog.draw(event.getGuiGraphics(), color, fogWeight * density);
    }

    @SubscribeEvent public static void overlay(RenderGuiEvent.Post event) {
        // As duas decisoes vem antes de pedir o GuiGraphics: no quadro comum nao ha nada a desenhar,
        // e este metodo roda a cada quadro de quem ja recebeu qualquer estado de area.
        boolean showBlackout = blackout >= .005F;
        boolean showVignette = vignette >= .005F && !Minecraft.getInstance().options.hideGui;
        if (!current() || !showBlackout && !showVignette) return;
        var gui = event.getGuiGraphics();
        int width = gui.guiWidth(), height = gui.guiHeight();
        if (showBlackout) gui.fill(0, 0, width, height, (int) (blackout * 255) << 24);
        if (!showVignette) return;
        // Fixed 12 rectangles: soft peripheral shadow, no image allocation or per-frame particles.
        for (int i = 0; i < 3; i++) {
            int marginX = width * i / 24, marginY = height * i / 24;
            int bandX = Math.max(1, width / 24), bandY = Math.max(1, height / 24);
            int alpha = (int) (vignette * (3 - i) / 3F * 180) << 24;
            gui.fill(marginX, marginY, width - marginX, marginY + bandY, alpha);
            gui.fill(marginX, height - marginY - bandY, width - marginX, height - marginY, alpha);
            gui.fill(marginX, marginY + bandY, marginX + bandX, height - marginY - bandY, alpha);
            gui.fill(width - marginX - bandX, marginY + bandY, width - marginX, height - marginY - bandY, alpha);
        }
    }
}
