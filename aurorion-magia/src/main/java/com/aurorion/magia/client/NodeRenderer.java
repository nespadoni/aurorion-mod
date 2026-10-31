package com.aurorion.magia.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.List;

/**
 * As tres cenas dos nos, em geometria luminosa — a mesma tecnica dos selos da cerimonia e das magias
 * ({@link SigilGeometry}): segmentos fixos escalados, somados como luz, sem textura e sem escrita.
 *
 * <ul>
 *   <li><b>No 1 — o fogo.</b> Selo maior girando no chao, anel de runas no sentido contrario, a aura em
 *       volta (parede de luz e lancas), a coluna de fogo torcida no centro e quatro chamas orbitando.</li>
 *   <li><b>No 2 — o medo.</b> Uma poça de escuridao com o selo de espinhos pulsando como coracao,
 *       rachaduras doentes, um olho no chao que pisca, olhos vermelhos abrindo no escuro em volta e
 *       raios caindo. A mente de quem entra escurece ({@link NodeClient#darkness}).</li>
 *   <li><b>No 3 — a expansao.</b> Tres circulos concentricos que vao a 19 blocos, aneis flutuantes como
 *       uma esfera armilar, um feixe de luz ate o ceu, espirais em volta dele, esferas orbitando, ondas
 *       que varrem o chao e uma coroa de aurora boreal pairando acima de tudo.</li>
 * </ul>
 *
 * <p>Tudo animado pelo tempo de jogo, que e o mesmo para todos: quem esta do outro lado da praça ve o
 * circulo no mesmo angulo. Sem alocar por quadro alem dos poucos vetores dos raios.
 */
public final class NodeRenderer {
    /** Uma onda varre o chao do No 3 a cada tanto. */
    static final int WAVE_PERIOD = 100;
    static final float AURORA_RADIUS = 24;
    static final float AURORA_BASE = 12;
    static final int ORBS = 8;
    private static final int AURORA_STRIPS = 64;
    private static final int BOLT_STEPS = 12;
    private static final float BOLT_TOP = 26;

    private static final int FIRE = 0xFF7A1A, GOLD = 0xFFD36B, EMBER = 0xC42A0A;
    private static final int VIOLET = 0x6B2FB3, SICK = 0x56E39F, PALE = 0xE2D6FF, INK = 0x050008, BLOOD = 0xFF2A2A;
    private static final int TEAL = 0x5CF2C6, SKY = 0x7AA8FF, LILAC = 0xC58BFF, WHITE = 0xF4FBFF;
    private static final int[] AURORA = {TEAL, SKY, LILAC};

    /** Pontos do raio da vez: so a thread de render mexe. */
    private static final float[] BOLT = new float[(BOLT_STEPS + 1) * 3];

    private NodeRenderer() {
    }

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        List<NodeClient.Scene> scenes = NodeClient.scenes();
        if (scenes.isEmpty()) return;
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) return;
        ResourceLocation dimension = level.dimension().location();
        Vec3 camera = event.getCamera().getPosition();
        Frustum frustum = event.getFrustum();
        boolean any = false;
        for (NodeClient.Scene scene : scenes) any |= visible(scene, dimension, camera, frustum);
        if (!any) return;

        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();

        VertexConsumer ink = buffers.getBuffer(SigilRenderer.inkType());
        for (NodeClient.Scene scene : scenes) {
            if (scene.index == 2 && visible(scene, dimension, camera, frustum)) {
                fearInk(pose, ink, camera, scene, scene.life(level, partial), scene.fade(level, partial));
            }
        }
        buffers.endBatch(SigilRenderer.inkType());

        VertexConsumer glow = buffers.getBuffer(SigilRenderer.glowType());
        for (NodeClient.Scene scene : scenes) {
            if (!visible(scene, dimension, camera, frustum)) continue;
            float life = scene.life(level, partial), fade = scene.fade(level, partial);
            if (fade <= .002F) continue;
            switch (scene.index) {
                case 1 -> fire(pose, glow, camera, scene, life, fade);
                case 2 -> fear(pose, glow, camera, scene, life, fade);
                default -> expanse(pose, glow, camera, scene, life, fade);
            }
        }
        buffers.endBatch(SigilRenderer.glowType());
    }

    private static boolean visible(NodeClient.Scene scene, ResourceLocation dimension, Vec3 camera, Frustum frustum) {
        return scene.dimension.equals(dimension) && scene.distanceSqr(camera) < scene.reach * scene.reach
                && frustum.isVisible(scene.bounds);
    }

    /** De 0 a 1 na abertura, desacelerando no fim: o circulo se abre rapido e assenta devagar. */
    private static float grow(float life) {
        float t = Mth.clamp(life / NodeClient.OPENING, 0, 1);
        float rest = 1 - t;
        return 1 - rest * rest * rest;
    }

    private static void origin(PoseStack pose, Vec3 camera, NodeClient.Scene s, double lift) {
        pose.pushPose();
        pose.translate(s.x - camera.x, s.y + lift - camera.y, s.z - camera.z);
    }

    // --- No 1 -----------------------------------------------------------------------------------

    private static void fire(PoseStack pose, VertexConsumer out, Vec3 camera, NodeClient.Scene s, float life, float fade) {
        float grow = grow(life), size = .2F + .8F * grow;
        float pulse = .85F + .15F * Mth.sin(life * .08F);
        origin(pose, camera, s, .03);
        Matrix4f m = pose.last().pose();
        Vec3 eye = camera.subtract(s.x, s.y + .03, s.z);

        SigilGeometry.disc(out, m, 5.4F * size, .01F, EMBER, .25F * fade * pulse, 32);
        pose.pushPose();
        SigilRenderer.spin(pose, life, .25F);
        SigilRenderer.layered(out, pose, SigilGeometry.SEAL_MAJOR, 4 * size, FIRE, GOLD, .95F * fade);
        pose.popPose();
        pose.pushPose();
        SigilRenderer.spin(pose, life, -.4F);
        SigilRenderer.layered(out, pose, SigilGeometry.RUNE_RING, 5 * size, GOLD, FIRE, .75F * fade);
        pose.popPose();
        SigilRenderer.layered(out, pose, SigilGeometry.CIRCLE, 1.3F * size, GOLD, FIRE, fade);

        // A aura: parede de luz baixa, lancas que sobem e descem, e a coluna de fogo torcida.
        SigilGeometry.wall(out, m, 4.15F * size, (2.4F + .5F * Mth.sin(life * .07F)) * grow, FIRE, .3F * fade * pulse, 0, 48);
        SigilGeometry.wall(out, m, 3.85F * size, 1.3F * grow, GOLD, .2F * fade, 0, 48);
        for (int i = 0; i < 20; i++) {
            float angle = i * Mth.TWO_PI / 20 + life * .004F;
            float height = (1.1F + 1.7F * Mth.abs(Mth.sin(life * .06F + i * .9F))) * grow;
            SigilGeometry.ray(out, m, angle, 4.05F * size, .14F, height, (i & 1) == 0 ? FIRE : GOLD, .4F * fade);
        }
        SigilGeometry.funnel(out, m, .35F, 1.1F, 5.5F * grow, life * .06F, FIRE, .5F * fade, 0, 16, 8);
        SigilGeometry.funnel(out, m, .2F, .6F, 7F * grow, -life * .09F, GOLD, .55F * fade, 0, 12, 8);

        // Quatro chamas em orbita. As contas sao as mesmas do NodeAmbience, que solta fogo em cima delas.
        for (int i = 0; i < 4; i++) {
            float angle = life * .04F + i * Mth.HALF_PI;
            float height = 1.8F + .35F * Mth.sin(life * .1F + i);
            float x = Mth.cos(angle) * 2.6F, z = Mth.sin(angle) * 2.6F;
            glare(out, m, x, height, z, eye, .75F, FIRE, .35F * fade);
            glare(out, m, x, height, z, eye, .28F, GOLD, .95F * fade);
        }

        if (life < NodeClient.OPENING) {
            float t = life / 30F;
            if (t < 1) SigilGeometry.band(out, m, 14 * t, 14 * t + 1.2F, .05F + t * 1.5F, GOLD, .7F * (1 - t) * fade, 48);
            float flash = Mth.clamp(1 - life / 25F, 0, 1);
            if (flash > 0) SigilGeometry.wall(out, m, 1.2F, 18, GOLD, .6F * flash, 0, 16);
        }
        pose.popPose();
    }

    // --- No 2 -----------------------------------------------------------------------------------

    /** A poça de escuridao e os espinhos pintados nela: tinta, para escurecer o chao de verdade. */
    private static void fearInk(PoseStack pose, VertexConsumer out, Vec3 camera, NodeClient.Scene s, float life, float fade) {
        float size = .2F + .8F * grow(life);
        origin(pose, camera, s, .02);
        SigilGeometry.solidDisc(out, pose.last().pose(), 7.5F * size, .015F, INK, .72F * fade, 0, 48);
        SigilRenderer.drawMesh(out, pose, SigilGeometry.SEAL_THORN, 6.2F * size, .06F, INK, .8F * fade, .02F);
        pose.popPose();
    }

    private static void fear(PoseStack pose, VertexConsumer out, Vec3 camera, NodeClient.Scene s, float life, float fade) {
        float grow = grow(life), size = .2F + .8F * grow;
        // Coracao lento: a cada 26 ticks uma batida curta, sobre uma respiracao mais longa.
        float beat = (float) Math.pow(Math.max(0, Mth.sin(life * Mth.TWO_PI / 26)), 6);
        float breath = .55F + .25F * Mth.sin(life * .05F) + .35F * beat;
        float flicker = .5F + .3F * Mth.sin(life * .9F) * Mth.sin(life * .37F);
        origin(pose, camera, s, .04);
        Matrix4f m = pose.last().pose();
        Vec3 eye = camera.subtract(s.x, s.y + .04, s.z);

        SigilRenderer.layered(out, pose, SigilGeometry.SEAL_THORN, 5 * size, VIOLET, PALE, breath * fade);
        SigilRenderer.layered(out, pose, SigilGeometry.CRACKS, 8.5F * size, SICK, VIOLET, .8F * flicker * fade);
        SigilGeometry.wall(out, m, 7.4F * size, 3.2F * grow, VIOLET, .12F * breath * fade, 0, 48);

        // O olho no chao, que pisca de vez em quando.
        float phase = life % 140;
        float open = phase < 8 ? Mth.abs(Mth.cos(phase / 8 * Mth.PI)) : 1;
        pose.pushPose();
        pose.scale(1, 1, Math.max(.05F, open));
        SigilRenderer.layered(out, pose, SigilGeometry.EYE, 1.8F * size, SICK, PALE, .9F * breath * fade);
        pose.popPose();

        if (eye.lengthSqr() < 44 * 44) eyesInTheDark(out, m, s, life, eye, fade);
        bolt(out, m, s, life, fade);

        if (life < NodeClient.OPENING) {
            float t = life / 40F;
            if (t < 1) SigilGeometry.band(out, m, 22 * t, 22 * t + 1.5F, .05F + t * .8F, VIOLET, .7F * (1 - t) * fade, 48);
        }
        pose.popPose();
    }

    /**
     * Pares de olhos abrindo no escuro em volta do no, cada par no seu ritmo: abre, encara, pisca, some.
     * Os lugares saem do sorteio do no, entao sao os mesmos para todos e nao mudam a cada quadro.
     */
    private static void eyesInTheDark(VertexConsumer out, Matrix4f m, NodeClient.Scene s, float life, Vec3 eye, float fade) {
        for (int i = 0; i < 7; i++) {
            float period = 160 + i * 23;
            float local = (life + NodeClient.noise(s.seed, 400 + i) * period) % period;
            if (local >= 90) continue;
            float alpha = Mth.clamp(local / 10, 0, 1) * Mth.clamp((90 - local) / 10, 0, 1);
            if (local > 45 && local < 50) alpha *= Mth.abs(Mth.cos((local - 45) / 5 * Mth.PI));
            if (alpha <= .01F) continue;
            float angle = NodeClient.noise(s.seed, 100 + i) * Mth.TWO_PI;
            float r = 11 + NodeClient.noise(s.seed, 200 + i) * 4;
            float x = Mth.cos(angle) * r, z = Mth.sin(angle) * r;
            float y = 1.4F + NodeClient.noise(s.seed, 300 + i) * .9F;
            // Lado a lado, sempre de frente para quem olha.
            float dx = (float) eye.x - x, dz = (float) eye.z - z;
            float length = Mth.sqrt(dx * dx + dz * dz);
            if (length < .01F) continue;
            float ox = -dz / length * .13F, oz = dx / length * .13F;
            int color = (i & 1) == 0 ? BLOOD : SICK;
            float a = alpha * fade;
            glare(out, m, x + ox, y, z + oz, eye, .22F, color, .25F * a);
            glare(out, m, x - ox, y, z - oz, eye, .22F, color, .25F * a);
            glare(out, m, x + ox, y, z + oz, eye, .07F, color, a);
            glare(out, m, x - ox, y, z - oz, eye, .07F, color, a);
        }
    }

    /** O raio da vez: do ceu ao chao em zigue-zague, com um galho, piscando ate apagar. */
    private static void bolt(VertexConsumer out, Matrix4f m, NodeClient.Scene s, float life, float fade) {
        long n = NodeClient.activeBolt(s, (long) life);
        if (n == Long.MIN_VALUE) return;
        float age = life - NodeClient.boltStart(s, n);
        float strength = Mth.clamp(1 - age / NodeClient.BOLT_TICKS, 0, 1) * fade;
        if (((int) age & 1) == 1) strength *= .5F;
        if (strength <= .01F) return;
        float angle = NodeClient.boltAngle(s, n), distance = NodeClient.boltDistance(s, n);
        float gx = Mth.cos(angle) * distance, gz = Mth.sin(angle) * distance;
        float tx = gx + (NodeClient.noise(s.seed, (int) n * 31 + 90) - .5F) * 6;
        float tz = gz + (NodeClient.noise(s.seed, (int) n * 31 + 91) - .5F) * 6;
        for (int i = 0; i <= BOLT_STEPS; i++) {
            float t = i / (float) BOLT_STEPS;
            float wobble = i == 0 || i == BOLT_STEPS ? 0 : 1.8F;
            BOLT[i * 3] = Mth.lerp(t, tx, gx) + (NodeClient.noise(s.seed, (int) n * 31 + i * 2) - .5F) * wobble;
            BOLT[i * 3 + 1] = BOLT_TOP * (1 - t);
            BOLT[i * 3 + 2] = Mth.lerp(t, tz, gz) + (NodeClient.noise(s.seed, (int) n * 31 + i * 2 + 1) - .5F) * wobble;
        }
        for (int i = 0; i < BOLT_STEPS; i++) {
            Vec3 a = new Vec3(BOLT[i * 3], BOLT[i * 3 + 1], BOLT[i * 3 + 2]);
            Vec3 b = new Vec3(BOLT[i * 3 + 3], BOLT[i * 3 + 4], BOLT[i * 3 + 5]);
            SigilGeometry.segment(out, m, a, b, .38F, VIOLET, .35F * strength);
            SigilGeometry.segment(out, m, a, b, .07F, PALE, .95F * strength);
        }
        // Um galho saindo do terco de cima, descendo para fora.
        Vec3 from = new Vec3(BOLT[12], BOLT[13], BOLT[14]);
        Vec3 fork = from.add((gx - tx) * .2 + 1.6, -3.5, (gz - tz) * .2 - 1.2);
        Vec3 tip = fork.add(1.1, -3, .9);
        SigilGeometry.segment(out, m, from, fork, .04F, PALE, .7F * strength);
        SigilGeometry.segment(out, m, fork, tip, .03F, PALE, .5F * strength);
        strike(out, m, gx, gz, strength);
    }

    /** O clarao no chao onde o raio bateu. */
    private static void strike(VertexConsumer out, Matrix4f m, float gx, float gz, float strength) {
        int core = SigilGeometry.argb(PALE, .55F * strength), rim = SigilGeometry.argb(VIOLET, 0);
        for (int i = 0; i < 16; i++) {
            float a = i * Mth.TWO_PI / 16, b = (i + 1) * Mth.TWO_PI / 16;
            out.addVertex(m, gx, .03F, gz).setColor(core);
            out.addVertex(m, gx, .03F, gz).setColor(core);
            out.addVertex(m, gx + 3 * Mth.cos(a), .03F, gz + 3 * Mth.sin(a)).setColor(rim);
            out.addVertex(m, gx + 3 * Mth.cos(b), .03F, gz + 3 * Mth.sin(b)).setColor(rim);
        }
    }

    // --- No 3 -----------------------------------------------------------------------------------

    private static void expanse(PoseStack pose, VertexConsumer out, Vec3 camera, NodeClient.Scene s, float life, float fade) {
        float grow = grow(life), size = .1F + .9F * grow;
        float flash = Mth.clamp(1 - life / 40F, 0, 1);
        origin(pose, camera, s, .03);
        Matrix4f m = pose.last().pose();
        Vec3 eye = camera.subtract(s.x, s.y + .03, s.z);

        // O chao: tres circulos ate 19 blocos e uma galaxia girando no meio.
        SigilGeometry.disc(out, m, 14 * size, .01F, TEAL, .14F * fade, 48);
        pose.pushPose();
        SigilRenderer.spin(pose, life, .1F);
        SigilRenderer.layered(out, pose, SigilGeometry.SEAL_MAJOR, 12 * size, TEAL, WHITE, .9F * fade);
        pose.popPose();
        pose.pushPose();
        SigilRenderer.spin(pose, life, -.15F);
        SigilRenderer.layered(out, pose, SigilGeometry.RUNE_RING, 16 * size, LILAC, SKY, .8F * fade);
        pose.popPose();
        SigilRenderer.layered(out, pose, SigilGeometry.CIRCLE, 19 * size, SKY, TEAL, .6F * fade);
        pose.pushPose();
        SigilRenderer.spin(pose, life, .3F);
        SigilRenderer.layered(out, pose, SigilGeometry.SPIRAL, 7 * size, WHITE, TEAL, .7F * fade);
        pose.popPose();

        // Aneis flutuando como uma esfera armilar, cada um girando para um lado.
        for (int k = 0; k < 3; k++) {
            pose.pushPose();
            pose.translate(0, (4 + k * 4) * grow, 0);
            SigilRenderer.spin(pose, life, (k & 1) == 0 ? .6F : -.8F);
            SigilRenderer.layered(out, pose, SigilGeometry.RUNE_RING, (6 - k * 1.5F) * size,
                    k == 1 ? LILAC : TEAL, WHITE, .75F * fade);
            pose.popPose();
        }

        // O feixe ate o ceu, mais forte na abertura, e as espirais em volta dele.
        float beam = fade * (1 + 2 * flash);
        SigilGeometry.wall(out, m, .8F, 160 * grow, WHITE, .35F * beam, 0, 12);
        SigilGeometry.wall(out, m, 1.8F, 120 * grow, TEAL, .15F * beam, 0, 16);
        SigilGeometry.funnel(out, m, 1.6F, 3.8F, 40 * grow, life * .05F, LILAC, .4F * fade, 0, 24, 16);
        SigilGeometry.funnel(out, m, 1.2F, 2.8F, 30 * grow, -life * .07F, TEAL, .35F * fade, 0, 24, 12);

        for (int i = 0; i < ORBS; i++) {
            float angle = life * .02F + i * Mth.TWO_PI / ORBS;
            float height = 3 + 1.5F * Mth.sin(life * .05F + i);
            float x = Mth.cos(angle) * 9, z = Mth.sin(angle) * 9;
            int color = AURORA[i % AURORA.length];
            glare(out, m, x, height, z, eye, 1.1F, color, .3F * fade);
            glare(out, m, x, height, z, eye, .35F, WHITE, .95F * fade);
        }

        aurora(out, m, life, fade * grow);

        float since = life - NodeClient.OPENING;
        if (since >= 0) {
            float wave = since % WAVE_PERIOD;
            if (wave < 40) {
                float t = wave / 40;
                SigilGeometry.band(out, m, 28 * t, 28 * t + 1.5F, .05F + t * .5F, TEAL, .5F * (1 - t) * fade, 64);
            }
        } else {
            float t = life / 50;
            if (t < 1) SigilGeometry.band(out, m, 40 * t, 40 * t + 3, .1F + t * 4, WHITE, .8F * (1 - t) * fade, 64);
        }
        pose.popPose();
    }

    /**
     * A coroa de aurora: duas cortinas de luz em anel, ondulando no raio, na altura e na cor. A borda de
     * baixo e a mais forte e o topo some no ceu, como a aurora de verdade.
     */
    private static void aurora(VertexConsumer out, Matrix4f m, float life, float alpha) {
        if (alpha <= .01F) return;
        for (int layer = 0; layer < 2; layer++) {
            float radius = AURORA_RADIUS - layer * 3.5F;
            float phase = layer * 1.7F;
            for (int i = 0; i < AURORA_STRIPS; i++) {
                float a0 = i * Mth.TWO_PI / AURORA_STRIPS, a1 = (i + 1) * Mth.TWO_PI / AURORA_STRIPS;
                float r0 = radius + 2.5F * Mth.sin(a0 * 3 + life * .02F + phase);
                float r1 = radius + 2.5F * Mth.sin(a1 * 3 + life * .02F + phase);
                float y0 = AURORA_BASE + 2 * Mth.sin(a0 * 5 + life * .03F + phase);
                float y1 = AURORA_BASE + 2 * Mth.sin(a1 * 5 + life * .03F + phase);
                float h0 = 14 + 5 * Mth.sin(a0 * 2 - life * .025F + phase);
                float h1 = 14 + 5 * Mth.sin(a1 * 2 - life * .025F + phase);
                float k0 = .15F + .25F * (.5F + .5F * Mth.sin(a0 * 4 + life * .04F + phase));
                float k1 = .15F + .25F * (.5F + .5F * Mth.sin(a1 * 4 + life * .04F + phase));
                int c0 = auroraColor(a0 / Mth.TWO_PI + life * .002F + layer * .33F);
                int c1 = auroraColor(a1 / Mth.TWO_PI + life * .002F + layer * .33F);
                float x0 = r0 * Mth.cos(a0), z0 = r0 * Mth.sin(a0), x1 = r1 * Mth.cos(a1), z1 = r1 * Mth.sin(a1);
                int b0 = SigilGeometry.argb(c0, k0 * alpha), b1 = SigilGeometry.argb(c1, k1 * alpha);
                int t0 = SigilGeometry.argb(c0, 0), t1 = SigilGeometry.argb(c1, 0);
                // A cortina, da borda acesa ao topo apagado.
                out.addVertex(m, x0, y0, z0).setColor(b0);
                out.addVertex(m, x1, y1, z1).setColor(b1);
                out.addVertex(m, x1, y1 + h1, z1).setColor(t1);
                out.addVertex(m, x0, y0 + h0, z0).setColor(t0);
                // A franja embaixo da borda, que tira o corte reto.
                out.addVertex(m, x0, y0 - 1.5F, z0).setColor(t0);
                out.addVertex(m, x1, y1 - 1.5F, z1).setColor(t1);
                out.addVertex(m, x1, y1, z1).setColor(b1);
                out.addVertex(m, x0, y0, z0).setColor(b0);
            }
        }
    }

    /** Verde-agua, azul e lilas em volta do anel, misturados sem degrau. */
    private static int auroraColor(float t) {
        float u = (t - Mth.floor(t)) * AURORA.length;
        int i = (int) u;
        int from = AURORA[i % AURORA.length], to = AURORA[(i + 1) % AURORA.length];
        float f = u - i;
        int r = (int) Mth.lerp(f, from >> 16 & 255, to >> 16 & 255);
        int g = (int) Mth.lerp(f, from >> 8 & 255, to >> 8 & 255);
        int b = (int) Mth.lerp(f, from & 255, to & 255);
        return r << 16 | g << 8 | b;
    }

    // --- Ajudantes -------------------------------------------------------------------------------

    /**
     * Um brilho redondo virado para quem olha: forte no centro, apagado na borda. As esferas, as chamas e
     * os olhos sao isto. {@code eye} e a camera em coordenadas da pose.
     */
    private static void glare(VertexConsumer out, Matrix4f m, float px, float py, float pz, Vec3 eye,
                              float radius, int color, float alpha) {
        if (alpha <= .004F) return;
        float nx = (float) eye.x - px, ny = (float) eye.y - py, nz = (float) eye.z - pz;
        float length = Mth.sqrt(nx * nx + ny * ny + nz * nz);
        if (length < .001F) return;
        nx /= length;
        ny /= length;
        nz /= length;
        float rx = -nz, rz = nx;
        float side = Mth.sqrt(rx * rx + rz * rz);
        if (side < .001F) {
            rx = 1;
            rz = 0;
        } else {
            rx /= side;
            rz /= side;
        }
        float ux = -rz * ny, uy = rz * nx - rx * nz, uz = rx * ny;
        int core = SigilGeometry.argb(color, alpha), rim = SigilGeometry.argb(color, 0);
        for (int i = 0; i < 12; i++) {
            float a = i * Mth.TWO_PI / 12, b = (i + 1) * Mth.TWO_PI / 12;
            float ca = Mth.cos(a) * radius, sa = Mth.sin(a) * radius;
            float cb = Mth.cos(b) * radius, sb = Mth.sin(b) * radius;
            out.addVertex(m, px, py, pz).setColor(core);
            out.addVertex(m, px, py, pz).setColor(core);
            out.addVertex(m, px + rx * ca + ux * sa, py + uy * sa, pz + rz * ca + uz * sa).setColor(rim);
            out.addVertex(m, px + rx * cb + ux * sb, py + uy * sb, pz + rz * cb + uz * sb).setColor(rim);
        }
    }
}
