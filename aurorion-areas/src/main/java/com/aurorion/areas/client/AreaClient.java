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
    private static int color, pulseRemaining;
    private static boolean flightBlocked;
    private AreaClient() {}
    public static void accept(AreaStatePayload payload) {
        dimension = payload.dimension();
        targetFog = Float.isFinite(payload.fogDistance()) ? Math.clamp(payload.fogDistance(), 0, 256) : 0;
        if (targetFog > 0) { lastFogDistance = targetFog; color = payload.fogColor(); }
        flightBlocked = payload.flightBlocked();
        pulseStrength = Float.isFinite(payload.vignette()) ? Math.clamp(payload.vignette(), 0, .9F) : 0;
        pulseRemaining = Math.clamp(payload.pulseTicks(), 0, 600);
    }
    private static boolean current() {
        var level = Minecraft.getInstance().level;
        return level != null && dimension != null && dimension.equals(level.dimension().location());
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (mc.isPaused()) return;
        if (!current()) { fogWeight = 0; vignette = 0; return; }
        fogWeight += ((targetFog > 0 ? 1 : 0) - fogWeight) * .08F;
        if (pulseRemaining > 0) pulseRemaining--;
        vignette += ((pulseRemaining > 0 ? pulseStrength : 0) - vignette) * .15F;
        // Avoid re-triggering local elytra animation while the server vetoes it.
        if (flightBlocked && mc.player != null) {
            if (mc.player.isFallFlying()) mc.player.stopFallFlying();
            mc.player.getAbilities().flying = false;
        }
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        dimension = null; targetFog = 0; fogWeight = 0; vignette = 0; pulseRemaining = 0; flightBlocked = false;
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
    @SubscribeEvent public static void overlay(RenderGuiEvent.Post event) {
        if (!current() || vignette < .005F || Minecraft.getInstance().options.hideGui) return;
        var gui = event.getGuiGraphics();
        int width = gui.guiWidth(), height = gui.guiHeight();
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
