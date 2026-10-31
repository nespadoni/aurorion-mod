package com.aurorion.intencao.client;

import com.aurorion.intencao.AurorionIntencao;
import com.aurorion.intencao.network.DuelPayloads;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4fStack;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * A arena de um duelo vista por quem está perto, toda desenhada no cliente a partir de um pacote só.
 * <ul>
 *   <li>uma <b>parede de luz</b> no raio do campo: sobe do chão na abertura, respira, tem faixas que correm em
 *   volta; violeta e dourado, ou vermelho no duelo até a morte;</li>
 *   <li>um <b>anel</b> brilhante rente ao chão;</li>
 *   <li><b>faíscas elétricas</b> subindo pela parede, na quantidade que a config de partículas de cada um
 *   permite.</li>
 * </ul>
 * Custo: um quad por segmento (64) por arena à vista, nenhum pacote por tick. Arena a mais de 96 blocos não é
 * desenhada; faíscas só até 48.
 */
@EventBusSubscriber(modid = AurorionIntencao.MOD_ID, value = Dist.CLIENT)
public final class ClientArena {
    private static final int SEGMENTS = 64;
    private static final float HEIGHT = 5.5f;
    private static final long GROW_MS = 900, FADE_MS = 1200;
    private static final double DRAW_SQR = 96 * 96, SPARK_SQR = 48 * 48;

    private record Arena(Vec3 center, float radius, boolean death, long openedAt, long closedAt) {
        Arena closed(long now) {
            return new Arena(center, radius, death, openedAt, now);
        }
    }

    private static final Map<UUID, Arena> ARENAS = new HashMap<>();

    /** Um raio de efeito: os pontos do zigue-zague ficam fixos durante a vida dele (~350 ms). */
    private record Bolt(Vec3 base, boolean death, long at, float[] xs, float[] zs) {
    }

    private static final java.util.ArrayDeque<Bolt> BOLTS = new java.util.ArrayDeque<>();
    private static final int BOLT_SEGMENTS = 10;
    private static final float BOLT_HEIGHT = 14f;
    private static final long BOLT_MS = 350;
    private static final RandomSource RANDOM = RandomSource.create();

    private ClientArena() {
    }

    static void accept(DuelPayloads.Arena payload) {
        UUID id = new UUID(payload.idHigh(), payload.idLow());
        long now = Util.getMillis();
        if (payload.open()) {
            if (!ARENAS.containsKey(id)) {
                ARENAS.put(id, new Arena(new Vec3(payload.x(), payload.y(), payload.z()), payload.radius(), payload.death(), now, 0));
            }
        } else {
            Arena arena = ARENAS.get(id);
            if (arena != null) ARENAS.put(id, arena.closed(now));
        }
    }

    /** O raio chegou: zigue-zague sorteado uma vez, faíscas, e um trovão só para quem está perto. */
    static void bolt(DuelPayloads.Bolt payload) {
        float[] xs = new float[BOLT_SEGMENTS + 1], zs = new float[BOLT_SEGMENTS + 1];
        for (int i = 1; i < BOLT_SEGMENTS; i++) {
            xs[i] = (RANDOM.nextFloat() - .5f) * 1.6f;
            zs[i] = (RANDOM.nextFloat() - .5f) * 1.6f;
        }
        if (BOLTS.size() >= 8) BOLTS.pollFirst();
        BOLTS.addLast(new Bolt(new Vec3(payload.x(), payload.y(), payload.z()), payload.death(), Util.getMillis(), xs, zs));
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        mc.level.playLocalSound(payload.x(), payload.y(), payload.z(), net.minecraft.sounds.SoundEvents.TRIDENT_THUNDER.value(),
                net.minecraft.sounds.SoundSource.PLAYERS, 0.8f, 1.2f, false);
        for (int i = 0; i < 16; i++) {
            mc.level.addParticle(ParticleTypes.END_ROD, payload.x(), payload.y() + 0.2, payload.z(),
                    (RANDOM.nextDouble() - .5) * .3, RANDOM.nextDouble() * .25, (RANDOM.nextDouble() - .5) * .3);
        }
    }

    /** 0 a 1: quanto a arena está de pé (sobe na abertura, desce no fim). */
    private static float presence(Arena a, long now) {
        float up = Mth.clamp((now - a.openedAt()) / (float) GROW_MS, 0, 1);
        float down = a.closedAt() == 0 ? 1 : 1 - Mth.clamp((now - a.closedAt()) / (float) FADE_MS, 0, 1);
        return (1 - (1 - up) * (1 - up)) * down;
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL || ARENAS.isEmpty() && BOLTS.isEmpty()) return;
        long now = Util.getMillis();
        Vec3 cam = event.getCamera().getPosition();
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.set(event.getModelViewMatrix());
        RenderSystem.applyModelViewMatrix();
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(event.getProjectionMatrix(), VertexSorting.DISTANCE_TO_ORIGIN);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        // Somando luz: a parede brilha sem escurecer o que está atrás.
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        for (Arena a : ARENAS.values()) {
            if (a.center().distanceToSqr(cam) > DRAW_SQR) continue;
            float shown = presence(a, now);
            if (shown <= 0.01f) continue;
            wall(a, cam, now, shown);
        }
        while (!BOLTS.isEmpty() && now - BOLTS.peekFirst().at() > BOLT_MS) BOLTS.pollFirst();
        for (Bolt bolt : BOLTS) drawBolt(bolt, cam, now);
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.restoreProjectionMatrix();
        modelView.popMatrix();
        RenderSystem.applyModelViewMatrix();
    }

    private static void wall(Arena a, Vec3 cam, long now, float shown) {
        float t = (now % 100_000L) / 1000f;
        float r = a.radius() * (0.25f + 0.75f * shown);
        float x0 = (float) (a.center().x - cam.x), y0 = (float) (a.center().y - cam.y) + 0.02f, z0 = (float) (a.center().z - cam.z);
        float h = HEIGHT * shown;
        int base = a.death() ? 0xFF3A30 : 0xB98CFF;
        int band = a.death() ? 0xFF9A40 : 0xFFD76A;
        float breathe = 0.75f + 0.25f * Mth.sin(t * 2.2f);
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < SEGMENTS; i++) {
            float a0 = Mth.TWO_PI * i / SEGMENTS, a1 = Mth.TWO_PI * (i + 1) / SEGMENTS;
            float c0 = Mth.cos(a0) * r, s0 = Mth.sin(a0) * r, c1 = Mth.cos(a1) * r, s1 = Mth.sin(a1) * r;
            // Faixas que correm em volta: três ondas de brilho girando.
            float run = 0.5f + 0.5f * Mth.sin(a0 * 3 - t * 2.6f);
            float glow = (0.18f + 0.22f * run) * breathe * shown;
            int color = mix(base, band, run * 0.6f);
            quad(b, x0 + c0, z0 + s0, x0 + c1, z0 + s1, y0, y0 + h, color, glow, 0);
            // O anel do chão: estreito e forte.
            quad(b, x0 + c0, z0 + s0, x0 + c1, z0 + s1, y0, y0 + 0.18f, band, 0.75f * shown, 0.55f * shown);
        }
        BufferUploader.drawWithShader(b.buildOrThrow());
    }

    /** O raio: duas fitas somando luz (núcleo claro, borda colorida), viradas para a câmera no eixo vertical. */
    private static void drawBolt(Bolt bolt, Vec3 cam, long now) {
        float life = 1 - (now - bolt.at()) / (float) BOLT_MS;
        if (life <= 0) return;
        float flicker = life * (0.7f + 0.3f * Mth.sin(now * 0.08f));
        double dx = cam.x - bolt.base().x, dz = cam.z - bolt.base().z;
        double len = Math.max(1e-3, Math.sqrt(dx * dx + dz * dz));
        // Largura perpendicular à linha de visão.
        float px = (float) (-dz / len), pz = (float) (dx / len);
        int edge = bolt.death() ? 0xFF4A30 : 0xC59AFF, core = bolt.death() ? 0xFFD0A0 : 0xFFF2C8;
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int pass = 0; pass < 2; pass++) {
            float half = pass == 0 ? 0.45f : 0.14f;
            int rgb = pass == 0 ? edge : core;
            float alpha = (pass == 0 ? 0.55f : 0.95f) * flicker;
            for (int i = 0; i < BOLT_SEGMENTS; i++) {
                float y0 = BOLT_HEIGHT * (1 - i / (float) BOLT_SEGMENTS), y1 = BOLT_HEIGHT * (1 - (i + 1) / (float) BOLT_SEGMENTS);
                float x0 = (float) (bolt.base().x - cam.x) + bolt.xs()[i], z0 = (float) (bolt.base().z - cam.z) + bolt.zs()[i];
                float x1 = (float) (bolt.base().x - cam.x) + bolt.xs()[i + 1], z1 = (float) (bolt.base().z - cam.z) + bolt.zs()[i + 1];
                float yb = (float) (bolt.base().y - cam.y);
                int r = rgb >> 16 & 255, g = rgb >> 8 & 255, bl = rgb & 255, a = (int) (Mth.clamp(alpha, 0, 1) * 255);
                b.addVertex(x0 - px * half, yb + y0, z0 - pz * half).setColor(r, g, bl, a);
                b.addVertex(x0 + px * half, yb + y0, z0 + pz * half).setColor(r, g, bl, a);
                b.addVertex(x1 + px * half, yb + y1, z1 + pz * half).setColor(r, g, bl, a);
                b.addVertex(x1 - px * half, yb + y1, z1 - pz * half).setColor(r, g, bl, a);
            }
        }
        BufferUploader.drawWithShader(b.buildOrThrow());
    }

    private static void quad(BufferBuilder b, float xa, float za, float xb, float zb, float yBottom, float yTop, int rgb,
                             float alphaBottom, float alphaTop) {
        int r = rgb >> 16 & 255, g = rgb >> 8 & 255, bl = rgb & 255;
        int ab = (int) (Mth.clamp(alphaBottom, 0, 1) * 255), at = (int) (Mth.clamp(alphaTop, 0, 1) * 255);
        b.addVertex(xa, yBottom, za).setColor(r, g, bl, ab);
        b.addVertex(xb, yBottom, zb).setColor(r, g, bl, ab);
        b.addVertex(xb, yTop, zb).setColor(r, g, bl, at);
        b.addVertex(xa, yTop, za).setColor(r, g, bl, at);
    }

    private static int mix(int a, int b, float t) {
        int r = (int) Mth.lerp(t, a >> 16 & 255, b >> 16 & 255);
        int g = (int) Mth.lerp(t, a >> 8 & 255, b >> 8 & 255);
        int bl = (int) Mth.lerp(t, a & 255, b & 255);
        return r << 16 | g << 8 | bl;
    }

    /** As faíscas: poucas, a cada 4 ticks, só perto e conforme a config de partículas de cada um. */
    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        if (ARENAS.isEmpty()) return;
        long now = Util.getMillis();
        Iterator<Arena> it = ARENAS.values().iterator();
        while (it.hasNext()) {
            Arena a = it.next();
            if (a.closedAt() != 0 && now - a.closedAt() > FADE_MS) it.remove();
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null || level.getGameTime() % 4 != 0) return;
        ParticleStatus status = mc.options.particles().get();
        int count = status == ParticleStatus.ALL ? 6 : status == ParticleStatus.DECREASED ? 2 : 0;
        if (count == 0) return;
        for (Arena a : ARENAS.values()) {
            if (a.closedAt() != 0 || a.center().distanceToSqr(player.position()) > SPARK_SQR) continue;
            for (int i = 0; i < count; i++) {
                double angle = RANDOM.nextDouble() * Math.PI * 2;
                double x = a.center().x + Math.cos(angle) * a.radius(), z = a.center().z + Math.sin(angle) * a.radius();
                double y = a.center().y + RANDOM.nextDouble() * 1.5;
                level.addParticle(a.death() ? ParticleTypes.FLAME : ParticleTypes.ELECTRIC_SPARK, x, y, z, 0, 0.06 + RANDOM.nextDouble() * 0.08, 0);
                if (i == 0) level.addParticle(ParticleTypes.END_ROD, x, y + 0.5, z, 0, 0.04, 0);
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ARENAS.clear();
        BOLTS.clear();
    }

    /** Trocou de dimensão (portal, teleporte): a arena ficou no outro mundo. */
    @SubscribeEvent
    public static void onClone(ClientPlayerNetworkEvent.Clone event) {
        ARENAS.clear();
    }
}
