package com.aurorion.limbo.client;

import com.aurorion.limbo.finale.FinaleScript;
import com.aurorion.limbo.network.FinalePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** Owner-only presentation. Music starts with the eyelids, never with the limbo HUD timer. */
public final class ClientFinale {
    private static FinalePayload state;
    private static double elapsed;
    private static long lastTick;
    private static Vec3 origin = Vec3.ZERO;
    private static float yaw;
    private static FinaleMusic music;
    private static FinaleScreen screen;
    private ClientFinale() { }

    public static void accept(FinalePayload payload) {
        if (payload.preview() || state != null && state.preview()) clear();
        state = payload;
        elapsed = payload.elapsedMillis();
        lastTick = System.nanoTime();
        if (screen != null) screen.rebuildText();
    }

    private static void captureCamera() {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;
        origin = mc.player.getEyePosition();
        yaw = mc.player.getYRot();
    }

    public static void tick() {
        if (state == null) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        long now = System.nanoTime();
        if (!mc.isPaused()) elapsed += Math.clamp((now - lastTick) / 1_000_000.0, 0, 5000);
        lastTick = now;
        if (state.preview() && elapsed >= state.script().totalMillis()) { clear(); return; }
        if (screen == null) {
            captureCamera();
            screen = new FinaleScreen();
        }
        if (mc.screen != screen) mc.setScreen(screen);
        // The same client tick opens the eyelids screen and starts the OGG.
        if (music == null && elapsed < script().deathTitleMillis() + 10_000) {
            mc.getMusicManager().stopPlaying();
            music = new FinaleMusic(ResourceLocation.parse(script().music()));
            mc.getSoundManager().play(music);
        }
    }

    public static boolean active() { return state != null; }
    public static boolean cinematic() { return state != null && screen != null; }
    public static boolean preview() { return state != null && state.preview(); }
    public static double elapsedMillis() {
        if (Minecraft.getInstance().isPaused()) return elapsed;
        return elapsed + Math.clamp((System.nanoTime() - lastTick) / 1_000_000.0, 0, 250);
    }
    public static FinaleScript script() { return state.script(); }
    public static Vec3 origin() { return origin; }
    public static float yaw() { return yaw; }
    public static double riseProgress() {
        return Math.clamp(elapsedMillis() / (script().riseSeconds() * 1000.0), 0, 1);
    }
    public static double cameraRise() {
        double t = riseProgress();
        return 32 * t * t * (3 - 2 * t);
    }

    public static void clear() {
        var mc = Minecraft.getInstance();
        state = null;
        if (music != null) mc.getSoundManager().stop(music);
        music = null;
        if (mc.screen instanceof FinaleScreen) mc.setScreen(null);
        screen = null;
        elapsed = 0; origin = Vec3.ZERO;
    }
}
