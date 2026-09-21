package com.aurorion.areas.client;

import com.aurorion.areas.AurorionAreas;
import com.aurorion.areas.network.AreaStatePayload;
import com.mojang.blaze3d.shaders.FogShape;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.FogType;
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
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        dimension = null; targetFog = 0; fogWeight = 0; vignette = 0; pulseRemaining = 0; flightBlocked = false;
        blackout = 0; blackoutStrength = 0; blackoutRemaining = 0;
    }
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void fog(ViewportEvent.RenderFog event) {
        if (!current() || fogWeight < .001F || event.getCamera().getFluidInCamera() != FogType.NONE
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
        if (!current() || fogWeight < .001F || event.getCamera().getFluidInCamera() != FogType.NONE) return;
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
