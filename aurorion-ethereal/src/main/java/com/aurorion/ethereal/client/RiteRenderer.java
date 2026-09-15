package com.aurorion.ethereal.client;

import com.aurorion.ethereal.ceremony.BindingRite;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.Arrays;

/** Geometria luminosa em batch: visivel mesmo quando o corpo do jogador sofre culling a distancia. */
public final class RiteRenderer {
    private static final RenderType RUNIC = RunicShard.create();
    private static final Quaternionf ROTATION = new Quaternionf();
    private static final float[][] GLYPHS = {
            {0,-1,0,1, 0,.9F,.7F,.35F, 0,.2F,.7F,-.3F},
            {-.6F,1,-.6F,-1, -.6F,1,.6F,.4F, .6F,.4F,.6F,-1},
            {0,-1,0,1, 0,.8F,.7F,0, .7F,0,0,-.5F},
            {-.65F,-1,.65F,1, -.65F,1,.65F,-1},
            {0,-1,0,1, -.7F,.35F,0,1, .7F,.35F,0,1},
            {-.6F,-.8F,.6F,.8F, -.6F,.8F,.6F,-.8F, -.6F,0,.6F,0}
    };
    private static final float[] OUTER = outerMesh();
    private static final float[] INNER = innerMesh();

    private RiteRenderer() {}

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        RiteClient.Rite rite = RiteClient.current();
        if (rite == null || rite.tick() < BindingRite.INTRO_TICKS
                || !event.getFrustum().isVisible(rite.bounds)) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !minecraft.level.dimension().location().equals(rite.dimension)) return;
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float life = rite.tick() + partial;
        float reveal = life - BindingRite.REVEAL_TICK;
        float formed = Mth.clamp((life - BindingRite.INTRO_TICKS) / BindingRite.RISE_TICKS, 0, 1);
        float charge = Mth.clamp((life - BindingRite.INTRO_TICKS - BindingRite.RISE_TICKS)
                / BindingRite.GATHER_TICKS, 0, 1);
        float opacity = formed * rite.fade(partial);
        if (opacity <= 0.001F) return;
        Vec3 camera = event.getCamera().getPosition();
        double x = rite.x(partial), y = rite.y(partial), z = rite.z(partial);
        double distance = camera.distanceToSqr(x, y, z);
        if (distance > BindingRite.AUDIENCE_RADIUS * BindingRite.AUDIENCE_RADIUS) return;
        int main = reveal >= 0 ? rite.color : 0xD9E8FF;
        int secondary = reveal >= 0 ? rite.secondary : 0x536A89;
        int accent = reveal >= 0 ? rite.accent : 0xFFF7DF;
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.last().pose().set(event.getModelViewMatrix());
        pose.translate(x - camera.x, y - camera.y + 0.045, z - camera.z);
        var buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer consumer = buffers.getBuffer(RUNIC);
        float radius = 2.3F * (0.4F + 0.6F * formed);
        if (reveal >= 0 && reveal < 16) radius += Mth.sin(reveal * Mth.PI / 16) * 0.28F;

        // Duas camadas independentes, cada qual pre-calculada uma unica vez.
        layer(pose, consumer, OUTER, life * 0.38F + charge * charge * 50, radius, main, secondary, opacity);
        layer(pose, consumer, INNER, -life * 0.65F - charge * charge * 65, radius, accent, secondary, opacity);

        if (reveal >= 0 && reveal < BindingRite.BURST_TICKS) {
            pose.pushPose();
            float wave = 1 + reveal * 0.16F;
            pose.scale(wave, 1, wave);
            mesh(consumer, pose.last().pose(), INNER, 0.024F, main,
                    opacity * (1 - reveal / BindingRite.BURST_TICKS), 0.025F);
            pose.popPose();
        }

        // Runas verticais orbitam o corpo. TraÃ§os vetoriais dispensam fontes ou entidades extras.
        if (distance < 64 * 64) {
            for (int i = 0; i < 6; i++) {
                float angle = life * 0.018F + i * Mth.TWO_PI / 6;
                pose.pushPose();
                pose.translate(Mth.cos(angle) * 1.6F, 0.75F + 0.3F * Mth.sin(life * 0.035F + i),
                        Mth.sin(angle) * 1.6F);
                pose.mulPose(event.getCamera().rotation());
                pose.mulPose(ROTATION.rotationX(Mth.HALF_PI));
                pose.scale(.22F, .22F, .22F);
                mesh(consumer, pose.last().pose(), GLYPHS[i], .14F, main, opacity * .22F, 0);
                mesh(consumer, pose.last().pose(), GLYPHS[i], .045F, accent, opacity * .85F, 0);
                pose.popPose();
            }
        }
        buffers.endBatch(RUNIC);

        if (reveal > 0) {
            float arriving = Mth.clamp(reveal / 16, 0, 1);
            float overshoot = 1 + Mth.sin(arriving * Mth.PI) * .18F;
            pose.pushPose();
            pose.translate(0, 4.4F + Mth.sin(life * .04F) * .08F, 0);
            pose.mulPose(event.getCamera().rotation());
            float scale = Math.min(.115F, 8F / Math.max(1, rite.titleWidth)) * overshoot;
            pose.scale(scale, -scale, scale);
            int alpha = Math.max(4, Math.round(255 * arriving * rite.fade(partial)));
            minecraft.font.drawInBatch(rite.titleText, -rite.titleWidth / 2F, 0,
                    alpha << 24 | main, true, pose.last().pose(), buffers,
                    Font.DisplayMode.NORMAL, (Math.round(alpha * .35F) << 24) | 0x080B18, LightTexture.FULL_BRIGHT);
            pose.popPose();
            buffers.endBatch();
        }
        pose.popPose();
    }

    private static void layer(PoseStack pose, VertexConsumer consumer, float[] lines, float spin,
                              float radius, int main, int secondary, float opacity) {
        pose.pushPose();
        pose.mulPose(ROTATION.rotationY(spin * Mth.DEG_TO_RAD));
        pose.scale(radius, 1, radius);
        Matrix4f matrix = pose.last().pose();
        mesh(consumer, matrix, lines, .045F, main, opacity * .14F, 0);
        mesh(consumer, matrix, lines, .021F, secondary, opacity * .9F, .002F);
        mesh(consumer, matrix, lines, .006F, main, opacity, .004F);
        pose.popPose();
    }

    /** Cada segmento vira um quad plano de espessura constante; sem alocar por frame. */
    private static void mesh(VertexConsumer out, Matrix4f matrix, float[] lines, float width,
                             int color, float opacity, float height) {
        int argb = Mth.clamp(Math.round(opacity * 255), 0, 255) << 24 | color & 0xFFFFFF;
        for (int i = 0; i < lines.length; i += 4) {
            float x1 = lines[i], z1 = lines[i + 1], x2 = lines[i + 2], z2 = lines[i + 3];
            float dx = x2 - x1, dz = z2 - z1;
            float length = Mth.sqrt(dx * dx + dz * dz);
            if (length < .0001F) continue;
            float nx = -dz / length * width, nz = dx / length * width;
            out.addVertex(matrix, x1 + nx, height, z1 + nz).setColor(argb);
            out.addVertex(matrix, x2 + nx, height, z2 + nz).setColor(argb);
            out.addVertex(matrix, x2 - nx, height, z2 - nz).setColor(argb);
            out.addVertex(matrix, x1 - nx, height, z1 - nz).setColor(argb);
        }
    }

    private static float[] outerMesh() {
        Mesh mesh = new Mesh();
        mesh.circle(0, 0, 1, 96);
        mesh.circle(0, 0, .95F, 96);
        mesh.circle(0, 0, .75F, 96);
        mesh.circle(0, 0, .71F, 96);
        for (int i = 0; i < 24; i++) {
            float angle = i * Mth.TWO_PI / 24;
            float sin = Mth.sin(angle), cos = Mth.cos(angle);
            float[] rune = GLYPHS[i % GLYPHS.length];
            for (int j = 0; j < rune.length; j += 4) {
                float x1 = rune[j] * .035F, z1 = .84F + rune[j + 1] * .065F;
                float x2 = rune[j + 2] * .035F, z2 = .84F + rune[j + 3] * .065F;
                mesh.line(x1 * cos - z1 * sin, x1 * sin + z1 * cos,
                        x2 * cos - z2 * sin, x2 * sin + z2 * cos);
            }
            mesh.line(.95F * cos, .95F * sin, cos, sin);
        }
        return mesh.finish();
    }

    private static float[] innerMesh() {
        Mesh mesh = new Mesh();
        mesh.circle(0, 0, .28F, 48);
        mesh.circle(0, 0, .31F, 48);
        for (int i = 0; i < 6; i++) {
            float angle = i * Mth.TWO_PI / 6;
            float next = angle + Mth.TWO_PI / 3;
            mesh.line(.64F * Mth.cos(angle), .64F * Mth.sin(angle),
                    .64F * Mth.cos(next), .64F * Mth.sin(next));
            mesh.circle(.57F * Mth.cos(angle), .57F * Mth.sin(angle), .09F, 24);
        }
        return mesh.finish();
    }

    private static final class Mesh {
        private final float[] lines = new float[4096];
        private int length;
        void line(float x1, float z1, float x2, float z2) {
            lines[length++] = x1; lines[length++] = z1;
            lines[length++] = x2; lines[length++] = z2;
        }
        void circle(float x, float z, float radius, int count) {
            for (int i = 0; i < count; i++) {
                float a = i * Mth.TWO_PI / count, b = (i + 1) * Mth.TWO_PI / count;
                line(x + radius * Mth.cos(a), z + radius * Mth.sin(a),
                        x + radius * Mth.cos(b), z + radius * Mth.sin(b));
            }
        }
        float[] finish() { return Arrays.copyOf(lines, length); }
    }

    private static final class RunicShard extends RenderStateShard {
        private RunicShard() { super("ethereal_rite", () -> {}, () -> {}); }
        static RenderType create() {
            return RenderType.create("aurorion_ethereal:rite", DefaultVertexFormat.POSITION_COLOR,
                    VertexFormat.Mode.QUADS, 32768, false, false, RenderType.CompositeState.builder()
                            .setShaderState(POSITION_COLOR_SHADER)
                            .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                            .setDepthTestState(LEQUAL_DEPTH_TEST)
                            .setCullState(NO_CULL)
                            .setWriteMaskState(COLOR_WRITE)
                            .setOutputState(PARTICLES_TARGET)
                            .createCompositeState(false));
        }
    }
}
