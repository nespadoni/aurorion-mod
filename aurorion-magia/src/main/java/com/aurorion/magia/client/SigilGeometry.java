package com.aurorion.magia.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.Arrays;

/**
 * Os desenhos dos selos e as primitivas que os pintam. Mesma tecnica dos selos da Cerimonia de
 * Vinculacao do {@code aurorion-ethereal}: cada selo e uma lista fixa de segmentos no plano XZ, de
 * raio 1, montada uma vez na carga da classe; desenhar e so escalar e emitir quads. Zero alocacao por
 * frame.
 */
final class SigilGeometry {
    /** Runas: segmentos em [-1,1], desenhadas pequenas em volta dos aneis. */
    private static final float[][] GLYPHS = {
            {0, -1, 0, 1, 0, .9F, .7F, .35F, 0, .2F, .7F, -.3F},
            {-.6F, 1, -.6F, -1, -.6F, 1, .6F, .4F, .6F, .4F, .6F, -1},
            {0, -1, 0, 1, 0, .8F, .7F, 0, .7F, 0, 0, -.5F},
            {-.65F, -1, .65F, 1, -.65F, 1, .65F, -1},
            {0, -1, 0, 1, -.7F, .35F, 0, 1, .7F, .35F, 0, 1},
            {-.6F, -.8F, .6F, .8F, -.6F, .8F, .6F, -.8F, -.6F, 0, .6F, 0}
    };

    /** Selo maior: anel duplo de runas e hexagrama. Genua Flecte, Transpositio, Sigillum, Imperium. */
    static final float[] SEAL_MAJOR = sealMajor();
    /** Selo de espinhos voltados para dentro: dor e queda. Cruciatus, Deiectio. */
    static final float[] SEAL_THORN = sealThorn();
    /** Selo de elos: correntes. Vinculum, Ferrum. */
    static final float[] SEAL_CHAIN = sealChain();
    /** Anel fino com marcas e runas: coroas, colares e aneis de pegada. */
    static final float[] RUNE_RING = runeRing();
    /** So a circunferencia. */
    static final float[] CIRCLE = circle();
    /** Relogio com os ponteiros parados: Tempus Sistere. */
    static final float[] SEAL_CLOCK = sealClock();
    /** Pentagrama dentro de dois aneis: Mortem Dico. */
    static final float[] SEAL_DEATH = sealDeath();
    /** Olho amendoado com iris e raios: Aspectus Captus. */
    static final float[] EYE = eye();
    /** Espiral de tres voltas: a marca do vento no chao. Turbilhao e Coluna de Vento. */
    static final float[] SPIRAL = spiral();
    /** Tres aneis concentricos quebrados: a crista da onda. Unda Magna, Carcer Aquae. */
    static final float[] SEAL_WAVE = sealWave();

    private SigilGeometry() {
    }

    /** Segmentos planos em XZ na altura {@code height}; cada um vira um quad de largura constante. */
    static void mesh(VertexConsumer out, Matrix4f matrix, float[] lines, float width, int color, float alpha,
                     float height) {
        int tint = argb(color, alpha);
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

    /** Disco aceso, forte no centro e apagado na borda. */
    static void disc(VertexConsumer out, Matrix4f m, float radius, float height, int color, float alpha, int steps) {
        int core = argb(color, alpha), rim = argb(color, 0);
        for (int i = 0; i < steps; i++) {
            float a = i * Mth.TWO_PI / steps, b = (i + 1) * Mth.TWO_PI / steps;
            out.addVertex(m, 0, height, 0).setColor(core);
            out.addVertex(m, 0, height, 0).setColor(core);
            out.addVertex(m, radius * Mth.cos(a), height, radius * Mth.sin(a)).setColor(rim);
            out.addVertex(m, radius * Mth.cos(b), height, radius * Mth.sin(b)).setColor(rim);
        }
    }

    /** Disco cheio de borda dura: a mancha de escuridao do Lux Vorata. */
    static void solidDisc(VertexConsumer out, Matrix4f m, float radius, float height, int color, float alpha,
                          float rimAlpha, int steps) {
        int core = argb(color, alpha), rim = argb(color, rimAlpha);
        for (int i = 0; i < steps; i++) {
            float a = i * Mth.TWO_PI / steps, b = (i + 1) * Mth.TWO_PI / steps;
            out.addVertex(m, 0, height, 0).setColor(core);
            out.addVertex(m, 0, height, 0).setColor(core);
            out.addVertex(m, radius * Mth.cos(a), height, radius * Mth.sin(a)).setColor(rim);
            out.addVertex(m, radius * Mth.cos(b), height, radius * Mth.sin(b)).setColor(rim);
        }
    }

    /** Anel horizontal cheio, apagado por dentro: ondas de choque. */
    static void band(VertexConsumer out, Matrix4f m, float inner, float outer, float height, int color, float alpha,
                     int steps) {
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
     * Parede vertical em volta do eixo: a barreira do Ventus Custos.
     *
     * <p>Sem tampa e sem fundo de proposito — o que se quer mostrar e "ate aqui", e nao uma redoma
     * fechada. As duas pontas tem alfa proprio: forte no chao, quase nada no alto, e a parede some em
     * vez de terminar num corte reto.
     */
    static void wall(VertexConsumer out, Matrix4f m, float radius, float height, int color,
                     float bottomAlpha, float topAlpha, int steps) {
        int low = argb(color, bottomAlpha), high = argb(color, topAlpha);
        for (int i = 0; i < steps; i++) {
            float a = i * Mth.TWO_PI / steps, b = (i + 1) * Mth.TWO_PI / steps;
            float ax = radius * Mth.cos(a), az = radius * Mth.sin(a);
            float bx = radius * Mth.cos(b), bz = radius * Mth.sin(b);
            out.addVertex(m, ax, 0, az).setColor(low);
            out.addVertex(m, bx, 0, bz).setColor(low);
            out.addVertex(m, bx, height, bz).setColor(high);
            out.addVertex(m, ax, height, az).setColor(high);
        }
    }

    /**
     * Funil: um cone oco, aberto em cima e estreito embaixo, com as faixas torcidas por
     * {@code twist} radianos a cada camada. E o corpo do furacao e o da coluna de vento.
     *
     * @param layers quantas faixas empilhadas; mais faixas, mais torcao visivel
     */
    static void funnel(VertexConsumer out, Matrix4f m, float bottomRadius, float topRadius, float height,
                       float twist, int color, float bottomAlpha, float topAlpha, int steps, int layers) {
        for (int layer = 0; layer < layers; layer++) {
            float t0 = layer / (float) layers, t1 = (layer + 1) / (float) layers;
            float r0 = Mth.lerp(t0, bottomRadius, topRadius), r1 = Mth.lerp(t1, bottomRadius, topRadius);
            float y0 = height * t0, y1 = height * t1;
            int c0 = argb(color, Mth.lerp(t0, bottomAlpha, topAlpha));
            int c1 = argb(color, Mth.lerp(t1, bottomAlpha, topAlpha));
            float s0 = twist * t0, s1 = twist * t1;
            // Uma faixa a cada duas: o vazio entre elas e o que faz o funil parecer girar.
            for (int i = 0; i < steps; i += 2) {
                float a = i * Mth.TWO_PI / steps, b = (i + 1) * Mth.TWO_PI / steps;
                out.addVertex(m, r0 * Mth.cos(a + s0), y0, r0 * Mth.sin(a + s0)).setColor(c0);
                out.addVertex(m, r0 * Mth.cos(b + s0), y0, r0 * Mth.sin(b + s0)).setColor(c0);
                out.addVertex(m, r1 * Mth.cos(b + s1), y1, r1 * Mth.sin(b + s1)).setColor(c1);
                out.addVertex(m, r1 * Mth.cos(a + s1), y1, r1 * Mth.sin(a + s1)).setColor(c1);
            }
        }
    }

    /** Lanca de luz em pe na borda de um selo, larga na base e apagada na ponta. */
    static void ray(VertexConsumer out, Matrix4f m, float angle, float distance, float halfWidth, float height,
                    int color, float alpha) {
        float cos = Mth.cos(angle), sin = Mth.sin(angle);
        float cx = distance * cos, cz = distance * sin;
        float nx = -sin * halfWidth, nz = cos * halfWidth;
        int base = argb(color, alpha), tip = argb(color, 0);
        out.addVertex(m, cx - nx, .01F, cz - nz).setColor(base);
        out.addVertex(m, cx + nx, .01F, cz + nz).setColor(base);
        out.addVertex(m, cx, height, cz).setColor(tip);
        out.addVertex(m, cx, height, cz).setColor(tip);
    }

    /**
     * Corrente entre dois pontos (coordenadas relativas a origem da pose). Cada elo e um losango, e os
     * elos alternam entre dois planos perpendiculares, como uma corrente de verdade. Cada traco e
     * desenhado em cruz para nao sumir quando visto de lado.
     */
    static void chain(VertexConsumer out, Matrix4f m, Vec3 from, Vec3 to, float linkLength, float width,
                      int color, float alpha) {
        Vec3 delta = to.subtract(from);
        double length = delta.length();
        if (length < 0.05) return;
        Vec3 dir = delta.scale(1 / length);
        Vec3 side = dir.cross(new Vec3(0, 1, 0));
        side = side.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : side.normalize();
        Vec3 normal = side.cross(dir).normalize();
        int links = Math.max(1, (int) (length / linkLength));
        double step = length / links;
        double half = step * 0.3;
        for (int i = 0; i < links; i++) {
            Vec3 a = from.add(dir.scale(i * step));
            Vec3 b = a.add(dir.scale(step));
            Vec3 mid = a.add(dir.scale(step / 2));
            Vec3 across = (i & 1) == 0 ? side : normal;
            Vec3 l = mid.add(across.scale(half));
            Vec3 r = mid.subtract(across.scale(half));
            segment(out, m, a, l, width, color, alpha);
            segment(out, m, l, b, width, color, alpha);
            segment(out, m, b, r, width, color, alpha);
            segment(out, m, r, a, width, color, alpha);
        }
    }

    /** Traco 3D: dois quads cruzados ao longo do segmento. */
    static void segment(VertexConsumer out, Matrix4f m, Vec3 a, Vec3 b, float width, int color, float alpha) {
        Vec3 dir = b.subtract(a);
        if (dir.lengthSqr() < 1.0E-8) return;
        dir = dir.normalize();
        Vec3 p1 = dir.cross(new Vec3(0, 1, 0));
        p1 = p1.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : p1.normalize();
        Vec3 p2 = dir.cross(p1).normalize();
        int tint = argb(color, alpha);
        quad(out, m, a, b, p1.scale(width), tint);
        quad(out, m, a, b, p2.scale(width), tint);
    }

    private static void quad(VertexConsumer out, Matrix4f m, Vec3 a, Vec3 b, Vec3 w, int tint) {
        out.addVertex(m, (float) (a.x + w.x), (float) (a.y + w.y), (float) (a.z + w.z)).setColor(tint);
        out.addVertex(m, (float) (b.x + w.x), (float) (b.y + w.y), (float) (b.z + w.z)).setColor(tint);
        out.addVertex(m, (float) (b.x - w.x), (float) (b.y - w.y), (float) (b.z - w.z)).setColor(tint);
        out.addVertex(m, (float) (a.x - w.x), (float) (a.y - w.y), (float) (a.z - w.z)).setColor(tint);
    }

    static int argb(int color, float opacity) {
        return Mth.clamp(Math.round(opacity * 255), 0, 255) << 24 | color & 0xFFFFFF;
    }

    // --- Desenhos ----------------------------------------------------------------------------

    private static float[] sealMajor() {
        Mesh mesh = new Mesh();
        mesh.circle(0, 0, 1, 72);
        mesh.circle(0, 0, .94F, 72);
        mesh.circle(0, 0, .72F, 64);
        runes(mesh, 16, .83F, .05F);
        for (int i = 0; i < 6; i++) {
            float angle = i * Mth.TWO_PI / 6;
            float next = angle + Mth.TWO_PI / 3;
            mesh.line(.7F * Mth.cos(angle), .7F * Mth.sin(angle), .7F * Mth.cos(next), .7F * Mth.sin(next));
            mesh.circle(.58F * Mth.cos(angle), .58F * Mth.sin(angle), .07F, 16);
        }
        mesh.circle(0, 0, .25F, 32);
        return mesh.finish();
    }

    private static float[] sealThorn() {
        Mesh mesh = new Mesh();
        mesh.circle(0, 0, 1, 72);
        mesh.circle(0, 0, .9F, 72);
        for (int i = 0; i < 12; i++) {
            float angle = i * Mth.TWO_PI / 12;
            float left = angle - .09F, right = angle + .09F;
            // Espinho: base larga na borda, ponta para o centro.
            mesh.line(.9F * Mth.cos(left), .9F * Mth.sin(left), .45F * Mth.cos(angle), .45F * Mth.sin(angle));
            mesh.line(.9F * Mth.cos(right), .9F * Mth.sin(right), .45F * Mth.cos(angle), .45F * Mth.sin(angle));
        }
        mesh.circle(0, 0, .3F, 32);
        runes(mesh, 6, .15F, .05F);
        return mesh.finish();
    }

    private static float[] sealChain() {
        Mesh mesh = new Mesh();
        mesh.circle(0, 0, 1, 72);
        mesh.circle(0, 0, .62F, 56);
        // Doze elos ovais em volta, o anel da corrente.
        for (int i = 0; i < 12; i++) {
            float angle = i * Mth.TWO_PI / 12;
            ellipse(mesh, .81F * Mth.cos(angle), .81F * Mth.sin(angle), .13F, .06F, angle + Mth.HALF_PI, 14);
        }
        runes(mesh, 8, .4F, .06F);
        return mesh.finish();
    }

    private static float[] runeRing() {
        Mesh mesh = new Mesh();
        mesh.circle(0, 0, 1, 56);
        for (int i = 0; i < 24; i++) {
            float angle = i * Mth.TWO_PI / 24;
            float inner = (i & 1) == 0 ? .86F : .92F;
            mesh.line(inner * Mth.cos(angle), inner * Mth.sin(angle), Mth.cos(angle), Mth.sin(angle));
        }
        runes(mesh, 6, 1.12F, .07F);
        return mesh.finish();
    }

    private static float[] sealClock() {
        Mesh mesh = new Mesh();
        mesh.circle(0, 0, 1, 96);
        mesh.circle(0, 0, .95F, 96);
        mesh.circle(0, 0, .78F, 80);
        for (int i = 0; i < 60; i++) {
            float angle = i * Mth.TWO_PI / 60;
            float inner = i % 15 == 0 ? .8F : i % 5 == 0 ? .86F : .91F;
            mesh.line(inner * Mth.cos(angle), inner * Mth.sin(angle), .95F * Mth.cos(angle), .95F * Mth.sin(angle));
        }
        runes(mesh, 12, .68F, .045F);
        // Ponteiros parados: o tempo nao anda dentro do selo.
        float hour = -Mth.HALF_PI + Mth.TWO_PI * 10 / 12;
        float minute = -Mth.HALF_PI + Mth.TWO_PI * 2 / 12;
        mesh.line(0, 0, .42F * Mth.cos(hour), .42F * Mth.sin(hour));
        mesh.line(0, 0, .6F * Mth.cos(minute), .6F * Mth.sin(minute));
        mesh.circle(0, 0, .05F, 16);
        mesh.circle(0, 0, .3F, 48);
        return mesh.finish();
    }

    private static float[] sealDeath() {
        Mesh mesh = new Mesh();
        mesh.circle(0, 0, 1, 80);
        mesh.circle(0, 0, .92F, 80);
        for (int i = 0; i < 5; i++) {
            float a = -Mth.HALF_PI + i * Mth.TWO_PI / 5;
            float b = -Mth.HALF_PI + (i + 2) * Mth.TWO_PI / 5;
            mesh.line(.88F * Mth.cos(a), .88F * Mth.sin(a), .88F * Mth.cos(b), .88F * Mth.sin(b));
            mesh.circle(.88F * Mth.cos(a), .88F * Mth.sin(a), .06F, 14);
        }
        mesh.circle(0, 0, .34F, 48);
        runes(mesh, 10, .96F, .03F);
        runes(mesh, 5, .18F, .05F);
        return mesh.finish();
    }

    private static float[] eye() {
        Mesh mesh = new Mesh();
        // Palpebras: dois arcos que se encontram nas pontas.
        int steps = 24;
        for (int i = 0; i < steps; i++) {
            float a = -1 + 2f * i / steps, b = -1 + 2f * (i + 1) / steps;
            float ya = .55F * (1 - a * a), yb = .55F * (1 - b * b);
            mesh.line(a, ya, b, yb);
            mesh.line(a, -ya, b, -yb);
        }
        mesh.circle(0, 0, .38F, 32);
        mesh.circle(0, 0, .16F, 20);
        for (int i = 0; i < 8; i++) {
            float angle = i * Mth.TWO_PI / 8;
            mesh.line(.2F * Mth.cos(angle), .2F * Mth.sin(angle), .34F * Mth.cos(angle), .34F * Mth.sin(angle));
        }
        // Cilios caindo como lagrimas.
        for (int i = -2; i <= 2; i++) {
            float x = i * .3F;
            float y = -.55F * (1 - x * x);
            mesh.line(x, y, x * 1.1F, y - .18F);
        }
        return mesh.finish();
    }

    private static float[] circle() {
        Mesh mesh = new Mesh();
        mesh.circle(0, 0, 1, 72);
        return mesh.finish();
    }

    /** Tres voltas fechando para o centro, com quatro braços saindo delas: a marca do vento. */
    private static float[] spiral() {
        Mesh mesh = new Mesh();
        int steps = 108;
        float turns = 3;
        float px = 1, pz = 0;
        for (int i = 1; i <= steps; i++) {
            float t = i / (float) steps;
            float angle = t * turns * Mth.TWO_PI;
            float radius = 1 - .82F * t;
            float x = radius * Mth.cos(angle), z = radius * Mth.sin(angle);
            mesh.line(px, pz, x, z);
            px = x;
            pz = z;
        }
        for (int i = 0; i < 4; i++) {
            float angle = i * Mth.HALF_PI + .35F;
            mesh.line(.35F * Mth.cos(angle), .35F * Mth.sin(angle), Mth.cos(angle), Mth.sin(angle));
        }
        mesh.circle(0, 0, .12F, 16);
        return mesh.finish();
    }

    /** Aneis quebrados em arcos desencontrados: agua em movimento, e nao um circulo parado. */
    private static float[] sealWave() {
        Mesh mesh = new Mesh();
        float[] radii = {1F, .78F, .56F};
        int[] arcs = {5, 4, 3};
        for (int ring = 0; ring < radii.length; ring++) {
            float radius = radii[ring];
            int count = arcs[ring];
            float gap = Mth.TWO_PI / count * .28F;
            float turn = ring * .4F;
            for (int i = 0; i < count; i++) {
                float from = turn + i * Mth.TWO_PI / count + gap;
                float to = turn + (i + 1) * Mth.TWO_PI / count - gap;
                int steps = 10;
                for (int s = 0; s < steps; s++) {
                    float a = from + (to - from) * s / steps;
                    float b = from + (to - from) * (s + 1) / steps;
                    mesh.line(radius * Mth.cos(a), radius * Mth.sin(a), radius * Mth.cos(b), radius * Mth.sin(b));
                }
            }
        }
        return mesh.finish();
    }

    /** Runas distribuidas num raio, cada uma girada para ficar de pe em relacao ao centro. */
    private static void runes(Mesh mesh, int count, float radius, float size) {
        for (int i = 0; i < count; i++) {
            float angle = i * Mth.TWO_PI / count;
            float sin = Mth.sin(angle), cos = Mth.cos(angle);
            float[] rune = GLYPHS[i % GLYPHS.length];
            for (int j = 0; j < rune.length; j += 4) {
                float x1 = rune[j] * size * .55F, z1 = radius + rune[j + 1] * size;
                float x2 = rune[j + 2] * size * .55F, z2 = radius + rune[j + 3] * size;
                mesh.line(x1 * cos - z1 * sin, x1 * sin + z1 * cos, x2 * cos - z2 * sin, x2 * sin + z2 * cos);
            }
        }
    }

    private static void ellipse(Mesh mesh, float cx, float cz, float rx, float rz, float turn, int count) {
        float cos = Mth.cos(turn), sin = Mth.sin(turn);
        for (int i = 0; i < count; i++) {
            float a = i * Mth.TWO_PI / count, b = (i + 1) * Mth.TWO_PI / count;
            float ax = rx * Mth.cos(a), az = rz * Mth.sin(a);
            float bx = rx * Mth.cos(b), bz = rz * Mth.sin(b);
            mesh.line(cx + ax * cos - az * sin, cz + ax * sin + az * cos, cx + bx * cos - bz * sin, cz + bx * sin + bz * cos);
        }
    }

    private static final class Mesh {
        private final float[] lines = new float[8192];
        private int length;

        void line(float x1, float z1, float x2, float z2) {
            lines[length++] = x1;
            lines[length++] = z1;
            lines[length++] = x2;
            lines[length++] = z2;
        }

        void circle(float x, float z, float radius, int count) {
            for (int i = 0; i < count; i++) {
                float a = i * Mth.TWO_PI / count, b = (i + 1) * Mth.TWO_PI / count;
                line(x + radius * Mth.cos(a), z + radius * Mth.sin(a), x + radius * Mth.cos(b), z + radius * Mth.sin(b));
            }
        }

        float[] finish() {
            return Arrays.copyOf(lines, length);
        }
    }
}
