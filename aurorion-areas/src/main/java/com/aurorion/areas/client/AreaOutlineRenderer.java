package com.aurorion.areas.client;

import com.aurorion.areas.AurorionAreas;
import com.aurorion.areas.network.AreaOutlinePayload;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

/**
 * Contorno da area desenhado no mundo, para a staff enxergar onde a regra comeca e termina.
 *
 * <p>O desenho fica <b>cravado no mundo</b>: o {@code PoseStack} do evento chega em identidade e a
 * rotacao da camera ja esta na pilha global do {@code RenderSystem}, entao a unica transformacao
 * aqui e a translacao relativa a camera. Carregar {@code getModelViewMatrix()} sobre a pilha giraria
 * tudo uma segunda vez e o contorno passaria a acompanhar a mira em vez de ficar parado.
 *
 * <p>Sem teste de profundidade: dentro de um predio, um limite que some atras da parede nao ajuda
 * quem precisa conferir ate onde vai a escola.
 */
@EventBusSubscriber(modid = AurorionAreas.MOD_ID, value = Dist.CLIENT)
public final class AreaOutlineRenderer {
    /** Azul = parte; vermelho = recorte. Mesma convencao da previa por particulas. */
    private static final int PART = 0x33CCFF, HOLE = 0xFF4D33;
    /** Faixa vertical visivel em volta da camera, em blocos, e a altura de cada banda do degrade. */
    private static final double WINDOW = 24, BAND = 2;
    /** Segmentos de um circulo: proporcional ao raio, para um circulo grande nao virar poligono. */
    private static final int MIN_STEPS = 24, MAX_STEPS = 128;
    private static final RenderType OUTLINE = OutlineShard.outline();

    @Nullable private static AreaOutlinePayload outline;
    private static int remaining;

    private AreaOutlineRenderer() {}

    public static void accept(AreaOutlinePayload payload) {
        boolean empty = payload.shapes().isEmpty();
        outline = empty ? null : payload;
        remaining = empty ? 0 : Mth.clamp(payload.ticks(), 0, 20 * 600);
    }
    private static void clear() { outline = null; remaining = 0; }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (remaining > 0 && !Minecraft.getInstance().isPaused() && --remaining == 0) clear();
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { clear(); }

    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        AreaOutlinePayload current = outline;
        if (current == null || remaining <= 0) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !minecraft.level.dimension().location().equals(current.dimension())) return;

        Vec3 camera = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        Matrix4f matrix = pose.last().pose();
        var buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer out = buffers.getBuffer(OUTLINE);
        // Some nos ultimos ticks em vez de sumir de uma vez: o corte seco parece um bug de render.
        float fade = Math.min(1, remaining / 20F);
        // Orcamento de geometria: uma area com 32 formas, em bandas de 2 blocos e circulos de 128
        // lados, passaria de 400 mil vertices por frame. A banda engrossa e o circulo perde lados
        // conforme o numero de formas cresce, mantendo o desenho legivel a custo praticamente fixo.
        int shapes = current.shapes().size();
        double band = Math.max(BAND, WINDOW * 2 * shapes / 96D);
        int maxSteps = (int) Mth.clamp(512D / shapes, 12, MAX_STEPS);
        for (AreaOutlinePayload.Shape shape : current.shapes()) draw(out, matrix, shape, camera, fade, band, maxSteps);
        buffers.endBatch(OUTLINE);
        pose.popPose();
    }

    private static void draw(VertexConsumer out, Matrix4f matrix, AreaOutlinePayload.Shape shape,
                             Vec3 camera, float fade, double band, int maxSteps) {
        int color = shape.hole() ? HOLE : PART;
        int count = shape.radius() > 0 ? steps(shape.radius(), maxSteps) : shape.xs().length;
        if (count < 2) return;

        // A parede aparece so em volta da camera: o limite util e o daqui, nao o de 300 blocos acima.
        double from = Math.max(shape.minY(), camera.y - WINDOW);
        double to = Math.min(shape.maxY(), camera.y + WINDOW);
        double ax = vertexX(shape, count, 0), az = vertexZ(shape, count, 0);
        for (int i = 0; i < count; i++) {
            int next = (i + 1) % count;
            double bx = vertexX(shape, count, next), bz = vertexZ(shape, count, next);
            // Piso e teto reais saem sempre, mesmo longe: sao eles que dizem ate onde a regra vale.
            ribbon(out, matrix, ax, az, bx, bz, shape.minY(), color, .85F * fade);
            ribbon(out, matrix, ax, az, bx, bz, shape.maxY(), color, .85F * fade);
            for (double y = Math.floor(from / band) * band; y < to; y += band) {
                double low = Math.max(y, from), high = Math.min(y + band, to);
                if (high - low < 1e-6) continue;
                wall(out, matrix, ax, az, bx, bz, low, high, color,
                        alpha(low, camera.y) * fade, alpha(high, camera.y) * fade);
            }
            ax = bx; az = bz;
        }
    }

    /** Opacidade da parede cai com a distancia vertical ate a camera, sem corte visivel na ponta. */
    private static float alpha(double y, double cameraY) {
        return (float) (0.30 * (1 - Mth.clamp(Math.abs(y - cameraY) / WINDOW, 0, 1)));
    }
    private static int steps(double radius, int maxSteps) {
        return (int) Mth.clamp(Math.round(radius * 2), Math.min(MIN_STEPS, maxSteps), maxSteps);
    }
    /** Circulo e amostrado aqui a partir do raio; poligono devolve o vertice recebido. */
    private static double vertexX(AreaOutlinePayload.Shape shape, int count, int i) {
        return shape.radius() <= 0 ? shape.xs()[i]
                : shape.xs()[0] + Math.cos(i * Math.PI * 2 / count) * shape.radius();
    }
    private static double vertexZ(AreaOutlinePayload.Shape shape, int count, int i) {
        return shape.radius() <= 0 ? shape.zs()[i]
                : shape.zs()[0] + Math.sin(i * Math.PI * 2 / count) * shape.radius();
    }

    /** Faixa horizontal fina marcando piso ou teto da forma. */
    private static void ribbon(VertexConsumer out, Matrix4f matrix, double ax, double az, double bx, double bz,
                               double y, int color, float alpha) {
        double dx = bx - ax, dz = bz - az;
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 1e-6) return;
        double nx = -dz / length * .06, nz = dx / length * .06;
        int tint = argb(color, alpha);
        out.addVertex(matrix, (float) (ax + nx), (float) y, (float) (az + nz)).setColor(tint);
        out.addVertex(matrix, (float) (bx + nx), (float) y, (float) (bz + nz)).setColor(tint);
        out.addVertex(matrix, (float) (bx - nx), (float) y, (float) (bz - nz)).setColor(tint);
        out.addVertex(matrix, (float) (ax - nx), (float) y, (float) (az - nz)).setColor(tint);
    }

    /** Banda vertical do contorno, com opacidade propria em cima e embaixo. */
    private static void wall(VertexConsumer out, Matrix4f matrix, double ax, double az, double bx, double bz,
                             double low, double high, int color, float alphaLow, float alphaHigh) {
        if (alphaLow <= 0.002F && alphaHigh <= 0.002F) return;
        int bottom = argb(color, alphaLow), top = argb(color, alphaHigh);
        out.addVertex(matrix, (float) ax, (float) low, (float) az).setColor(bottom);
        out.addVertex(matrix, (float) bx, (float) low, (float) bz).setColor(bottom);
        out.addVertex(matrix, (float) bx, (float) high, (float) bz).setColor(top);
        out.addVertex(matrix, (float) ax, (float) high, (float) az).setColor(top);
    }

    private static int argb(int rgb, float alpha) {
        return (Math.round(Mth.clamp(alpha, 0, 1) * 255) << 24) | (rgb & 0xFFFFFF);
    }

    private static final class OutlineShard extends RenderStateShard {
        private OutlineShard() { super("aurorion_areas_outline", () -> {}, () -> {}); }
        static RenderType outline() {
            return RenderType.create("aurorion_areas:outline", DefaultVertexFormat.POSITION_COLOR,
                    VertexFormat.Mode.QUADS, 32768, false, false, RenderType.CompositeState.builder()
                            .setShaderState(POSITION_COLOR_SHADER)
                            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                            .setDepthTestState(NO_DEPTH_TEST)
                            .setCullState(NO_CULL)
                            .setWriteMaskState(COLOR_WRITE)
                            .setOutputState(PARTICLES_TARGET)
                            .createCompositeState(false));
        }
    }
}
