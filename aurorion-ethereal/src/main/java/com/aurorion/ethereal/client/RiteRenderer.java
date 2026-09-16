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

/**
 * Geometria luminosa em batch: visivel mesmo quando o corpo do jogador sofre culling a distancia.
 *
 * <p>A cena e <b>parada</b>. O selo nao gira e as runas nao orbitam: numa plateia de trinta pessoas
 * espalhadas em volta, qualquer giro faz cada uma ver um desenho diferente no mesmo instante. O
 * unico elemento que ainda acompanha quem olha e o nome da casa, e so no eixo Y — ele fica em pe,
 * sempre no mesmo ponto acima da cabeca, em vez de tombar junto com a mira de cada espectador.
 */
public final class RiteRenderer {
    private static final RenderType RUNIC = RunicShard.runic();
    private static final RenderType GLOW = RunicShard.glow();
    private static final Quaternionf ROTATION = new Quaternionf();
    /** Torcao fixa do hexagrama: separa as duas camadas sem precisar gira-las uma contra a outra. */
    private static final float INNER_TURN = 30F;
    /** Lancas de luz em volta do selo. */
    private static final int RAYS = 16;
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
        // O PoseStack do evento chega em identidade e a rotacao da camera ja esta na pilha global do
        // RenderSystem (LevelRenderer faz matrix4fstack.mul(frustumMatrix) antes de renderizar).
        // getModelViewMatrix() e justamente esse frustumMatrix: carrega-lo aqui girava o selo uma
        // segunda vez, e era por isso que ele acompanhava a mira em vez de ficar cravado no chao.
        // So a translacao relativa a camera e necessaria; o resto do metodo desenha em espaco de mundo.
        pose.translate(x - camera.x, y - camera.y + 0.045, z - camera.z);
        var buffers = minecraft.renderBuffers().bufferSource();
        float radius = 2.3F * (0.4F + 0.6F * formed);
        if (reveal >= 0 && reveal < 16) radius += Mth.sin(reveal * Mth.PI / 16) * 0.28F;
        // O selo respira no lugar. Antes esse papel era do giro, que virou ruido com a plateia em
        // volta: um pulso de brilho carrega a mesma tensao e se ve igual de qualquer angulo.
        float breath = 0.88F + 0.12F * Mth.sin(life * 0.09F) + 0.25F * charge;

        aura(pose, buffers.getBuffer(GLOW), life, charge, reveal, radius, breath, main, accent, opacity);
        buffers.endBatch(GLOW);

        VertexConsumer consumer = buffers.getBuffer(RUNIC);
        // Duas camadas cravadas nos pes; o hexagrama so nasce torcido em relacao ao anel de runas.
        layer(pose, consumer, OUTER, 0, radius, main, secondary, opacity);
        layer(pose, consumer, INNER, INNER_TURN, radius, accent, secondary, opacity);

        if (reveal >= 0 && reveal < BindingRite.BURST_TICKS) {
            pose.pushPose();
            float wave = 1 + reveal * 0.16F;
            pose.scale(wave, 1, wave);
            mesh(consumer, pose.last().pose(), INNER, 0.024F, main,
                    opacity * (1 - reveal / BindingRite.BURST_TICKS), 0.025F);
            pose.popPose();
        }

        // Runas verticais paradas em volta do corpo, viradas para fora do circulo. Tracos vetoriais
        // dispensam fontes ou entidades extras.
        if (distance < 64 * 64) {
            for (int i = 0; i < 6; i++) {
                float angle = i * Mth.TWO_PI / 6;
                pose.pushPose();
                pose.translate(Mth.cos(angle) * 1.6F, 0.75F + 0.3F * Mth.sin(life * 0.035F + i),
                        Mth.sin(angle) * 1.6F);
                pose.mulPose(ROTATION.rotationY(Mth.HALF_PI - angle));
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
            // So o eixo Y. Com a rotacao inteira da camera o nome tombava junto com a mira de quem
            // olhava e, de perto ou de baixo, escorregava para fora do selo — cada espectador via o
            // nome num lugar. Travando a inclinacao, ele fica em pe e ancorado acima da cabeca.
            pose.mulPose(ROTATION.rotationY(-event.getCamera().getYRot() * Mth.DEG_TO_RAD));
            float scale = Math.min(.115F, 8F / Math.max(1, rite.titleWidth)) * overshoot;
            float strength = arriving * rite.fade(partial);
            VertexConsumer halo = buffers.getBuffer(GLOW);
            panel(halo, pose.last().pose(), rite.titleWidth * scale * .62F + .45F,
                    scale * 13F, -scale * 4.5F, main, .34F * strength);
            buffers.endBatch(GLOW);
            pose.scale(scale, -scale, scale);
            int alpha = Math.max(4, Math.round(255 * strength));
            minecraft.font.drawInBatch(rite.titleText, -rite.titleWidth / 2F, 0,
                    alpha << 24 | main, true, pose.last().pose(), buffers,
                    Font.DisplayMode.NORMAL, (Math.round(alpha * .35F) << 24) | 0x080B18, LightTexture.FULL_BRIGHT);
            pose.popPose();
            buffers.endBatch();
        }
        pose.popPose();
    }

    /**
     * A luz da casa em volta do selo: chao aceso, lancas verticais, coluna central e a onda que abre
     * na revelacao.
     *
     * <p>Tudo aditivo e sem escrever profundidade, para somar sobre o mundo como luz em vez de
     * cobri-lo como tinta — e para nunca tapar as linhas do selo nem as letras do nome.
     */
    private static void aura(PoseStack pose, VertexConsumer glow, float life, float charge,
                             float reveal, float radius, float breath, int main, int accent, float opacity) {
        Matrix4f matrix = pose.last().pose();
        float grow = reveal >= 0 ? 1 : 0.35F + 0.65F * charge;
        float power = opacity * breath * grow;

        disc(glow, matrix, radius * 1.15F, .012F, main, .16F * power, 24);

        for (int i = 0; i < RAYS; i++) {
            float angle = i * Mth.TWO_PI / RAYS;
            float height = (1.35F + 1.05F * Mth.abs(Mth.sin(life * .045F + i * .8F))) * grow;
            ray(glow, matrix, angle, radius * .99F, .16F, height,
                    (i & 1) == 0 ? main : accent, .30F * power);
        }

        // A coluna so fecha depois que a casa aparece; antes ela e apenas o que o rito juntou.
        float column = (3.2F + 2.6F * grow) * (reveal >= 0 ? 1 : charge);
        if (column > .05F) {
            prism(glow, matrix, .52F, column, main, .20F * power);
            prism(glow, matrix, .17F, column * 1.35F, accent, .30F * power);
        }

        if (reveal >= 0 && reveal < 34) {
            for (int i = 0; i < 2; i++) {
                float wave = reveal - i * 9;
                if (wave < 0 || wave > 25) continue;
                float progress = wave / 25;
                float ring = radius * (.35F + 1.9F * progress);
                band(glow, matrix, ring, ring + .55F, .05F + progress * 2.6F,
                        i == 0 ? accent : main, .55F * (1 - progress) * opacity, 40);
            }
        }
    }

    private static void layer(PoseStack pose, VertexConsumer consumer, float[] lines, float turn,
                              float radius, int main, int secondary, float opacity) {
        pose.pushPose();
        pose.mulPose(ROTATION.rotationY(turn * Mth.DEG_TO_RAD));
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
        int tint = argb(color, opacity);
        for (int i = 0; i < lines.length; i += 4) {
            float x1 = lines[i], z1 = lines[i + 1], x2 = lines[i + 2], z2 = lines[i + 3];
            float dx = x2 - x1, dz = z2 - z1;
            float length = Mth.sqrt(dx * dx + dz * dz);
            if (length < .0001F) continue;
            float nx = -dz / length * width, nz = dx / length * width;
            out.addVertex(matrix, x1 + nx, height, z1 + nz).setColor(tint);
            out.addVertex(matrix, x2 + nx, height, z2 + nz).setColor(tint);
            out.addVertex(matrix, x2 - nx, height, z2 - nz).setColor(tint);
            out.addVertex(matrix, x1 - nx, height, z1 - nz).setColor(tint);
        }
    }

    /** Triangulo em pe, largo na base e apagado na ponta: uma lanca de luz. */
    private static void ray(VertexConsumer out, Matrix4f m, float angle, float distance,
                            float halfWidth, float height, int color, float alpha) {
        float cos = Mth.cos(angle), sin = Mth.sin(angle);
        float cx = distance * cos, cz = distance * sin;
        float nx = -sin * halfWidth, nz = cos * halfWidth;
        int base = argb(color, alpha), tip = argb(color, 0);
        out.addVertex(m, cx - nx, .01F, cz - nz).setColor(base);
        out.addVertex(m, cx + nx, .01F, cz + nz).setColor(base);
        out.addVertex(m, cx, height, cz).setColor(tip);
        out.addVertex(m, cx, height, cz).setColor(tip);
    }

    /** Quatro faces verticais: a coluna de luz que desce sobre a pessoa. */
    private static void prism(VertexConsumer out, Matrix4f m, float half, float height,
                              int color, float alpha) {
        int base = argb(color, alpha), tip = argb(color, 0);
        for (int i = 0; i < 4; i++) {
            float a = i * Mth.HALF_PI, b = a + Mth.HALF_PI;
            float x1 = Mth.cos(a) * half, z1 = Mth.sin(a) * half;
            float x2 = Mth.cos(b) * half, z2 = Mth.sin(b) * half;
            out.addVertex(m, x1, .01F, z1).setColor(base);
            out.addVertex(m, x2, .01F, z2).setColor(base);
            out.addVertex(m, x2, height, z2).setColor(tip);
            out.addVertex(m, x1, height, z1).setColor(tip);
        }
    }

    /** Disco aceso, forte no centro e apagado na borda. */
    private static void disc(VertexConsumer out, Matrix4f m, float radius, float height,
                             int color, float alpha, int steps) {
        int core = argb(color, alpha), rim = argb(color, 0);
        for (int i = 0; i < steps; i++) {
            float a = i * Mth.TWO_PI / steps, b = (i + 1) * Mth.TWO_PI / steps;
            out.addVertex(m, 0, height, 0).setColor(core);
            out.addVertex(m, 0, height, 0).setColor(core);
            out.addVertex(m, radius * Mth.cos(a), height, radius * Mth.sin(a)).setColor(rim);
            out.addVertex(m, radius * Mth.cos(b), height, radius * Mth.sin(b)).setColor(rim);
        }
    }

    /** Anel horizontal cheio: a onda que abre na revelacao. */
    private static void band(VertexConsumer out, Matrix4f m, float inner, float outer, float height,
                             int color, float alpha, int steps) {
        int bright = argb(color, alpha), edge = argb(color, 0);
        for (int i = 0; i < steps; i++) {
            float a = i * Mth.TWO_PI / steps, b = (i + 1) * Mth.TWO_PI / steps;
            out.addVertex(m, inner * Mth.cos(a), height, inner * Mth.sin(a)).setColor(edge);
            out.addVertex(m, inner * Mth.cos(b), height, inner * Mth.sin(b)).setColor(edge);
            out.addVertex(m, outer * Mth.cos(b), height, outer * Mth.sin(b)).setColor(bright);
            out.addVertex(m, outer * Mth.cos(a), height, outer * Mth.sin(a)).setColor(bright);
        }
    }

    /**
     * Retangulo aceso com as quatro bordas apagadas: o clarao atras das letras do nome.
     *
     * <p>Tres colunas (apaga, cheio, apaga) resolvidas por aritmetica no lugar de tabelas: este
     * metodo roda a cada frame durante a coroacao, e a meta de zero alocacao por frame da SDD §2
     * vale aqui mesmo com uma cena unica na tela.
     */
    private static void panel(VertexConsumer out, Matrix4f m, float halfWidth, float halfHeight,
                              float centerY, int color, float alpha) {
        float core = halfWidth * .5F;
        int off = argb(color, 0), full = argb(color, alpha);
        for (int i = 0; i < 3; i++) {
            float x1 = i == 0 ? -halfWidth : i == 1 ? -core : core;
            float x2 = i == 0 ? -core : i == 1 ? core : halfWidth;
            int left = i == 0 ? off : full, right = i == 2 ? off : full;
            out.addVertex(m, x1, centerY - halfHeight, .02F).setColor(off);
            out.addVertex(m, x2, centerY - halfHeight, .02F).setColor(off);
            out.addVertex(m, x2, centerY, .02F).setColor(right);
            out.addVertex(m, x1, centerY, .02F).setColor(left);
            out.addVertex(m, x1, centerY, .02F).setColor(left);
            out.addVertex(m, x2, centerY, .02F).setColor(right);
            out.addVertex(m, x2, centerY + halfHeight, .02F).setColor(off);
            out.addVertex(m, x1, centerY + halfHeight, .02F).setColor(off);
        }
    }

    private static int argb(int color, float opacity) {
        return Mth.clamp(Math.round(opacity * 255), 0, 255) << 24 | color & 0xFFFFFF;
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

        /** Tinta translucida: as linhas do selo cobrem o que esta atras. */
        static RenderType runic() {
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

        /** Luz de verdade: soma no que ja esta na tela (SRC_ALPHA, ONE), entao clareia em vez de pintar. */
        static RenderType glow() {
            return RenderType.create("aurorion_ethereal:rite_glow", DefaultVertexFormat.POSITION_COLOR,
                    VertexFormat.Mode.QUADS, 32768, false, false, RenderType.CompositeState.builder()
                            .setShaderState(POSITION_COLOR_SHADER)
                            .setTransparencyState(LIGHTNING_TRANSPARENCY)
                            .setDepthTestState(LEQUAL_DEPTH_TEST)
                            .setCullState(NO_CULL)
                            .setWriteMaskState(COLOR_WRITE)
                            .setOutputState(PARTICLES_TARGET)
                            .createCompositeState(false));
        }
    }
}
