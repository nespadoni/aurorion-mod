package com.aurorion.ficha.client;

import com.aurorion.ficha.sheet.RollResult;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.jetbrains.annotations.Nullable;

/**
 * O d20 desenhado: o hexágono da silhueta do icosaedro com a face triangular da frente, em três
 * tons, e o número no meio. Sem textura — linhas de {@code fill}, nítido em qualquer escala de GUI.
 *
 * <h2>A rolagem</h2>
 * Nos primeiros {@link #SPIN_MS} o número troca cada vez mais devagar (sorteio local, só visual), as
 * facetas alternam o tom no mesmo passo e o dado quica cada vez mais baixo; depois pousa no natural
 * de verdade: cresce um pouco e assenta ({@link #pop}, sem salto de tamanho) e o resultado entra
 * logo depois ({@link #reveal}). Natural 20 solta faíscas douradas; natural 1 treme. Com
 * vantagem/desvantagem o segundo d20 gira junto, menor, e ao pousar fica apagado e riscado — na
 * frente do principal, para o número descartado continuar legível. Tudo derivado do relógio: nada
 * guardado por frame, e os números saem de uma tabela (nenhuma {@code String} nova por frame).
 */
final class D20 {
    static final long SPIN_MS = 800L;
    static final long POP_MS = 240L;
    /** O resultado (total, sucesso/falha) entra depois de o dado assentar, em {@link #REVEAL_MS}. */
    static final long REVEAL_DELAY_MS = 60L;
    static final long REVEAL_MS = 200L;
    private static final long SPARK_MS = 650L;
    private static final long SHAKE_MS = 380L;
    /** Trocas de número durante o giro. */
    private static final int STEPS = 14;

    /** Paletas: base, face clara, borda, número. */
    private static final int[] VIOLET = {0xFF4A2F8F, 0xFF7C5CD6, 0xFF1E1240, 0xFFFFF4DA};
    private static final int[] GOLD = {0xFF9A6A12, 0xFFF2C14E, 0xFF4A3005, 0xFF2A1700};
    private static final int[] RED = {0xFF7A1A28, 0xFFD9475B, 0xFF33080F, 0xFFFFE6EA};
    private static final int[] GREEN = {0xFF1F6B45, 0xFF4ADE80, 0xFF0B2A1A, 0xFF07170E};
    private static final int[] DIM = {0xFF2A2440, 0xFF4A4266, 0xFF14102A, 0xFFBFB3D6};

    /** Os números que o dado mostra (0–199) já como texto: o desenho é por frame. */
    private static final String[] NUMBERS = new String[200];

    static {
        for (int i = 0; i < NUMBERS.length; i++) NUMBERS[i] = Integer.toString(i);
    }

    private D20() {
    }

    /** {@code value} como texto, da tabela quando couber. */
    static String num(int value) {
        return value >= 0 && value < NUMBERS.length ? NUMBERS[value] : Integer.toString(value);
    }

    /** O dado está girando em {@code elapsed} ms desde o início da rolagem? */
    static boolean spinning(long elapsed) {
        return elapsed < SPIN_MS;
    }

    /** O passo do giro: {@code t^0,55} faz as trocas rápidas no começo e espaçadas perto do fim. */
    private static long step(long elapsed) {
        return (long) (Math.pow(Math.min(1.0, elapsed / (double) SPIN_MS), 0.55) * STEPS);
    }

    /**
     * O número a mostrar: durante o giro, um sorteio estável por passo; depois, o natural. O natural
     * não aparece antes da hora (seria spoiler).
     */
    static int face(long elapsed, int natural, int sides, long seed) {
        if (!spinning(elapsed)) return natural;
        long step = step(elapsed);
        long mix = (seed ^ (step * 0x9E3779B97F4A7C15L)) * 0xBF58476D1CE4E5B9L;
        int s = Math.max(2, sides);
        int value = (int) Math.floorMod(mix >>> 17, (long) s) + 1;
        return value == natural ? (value % s) + 1 : value;
    }

    /** As facetas trocam de tom junto com o número: o "giro" desacelera com ele. */
    static boolean flip(long elapsed) {
        return spinning(elapsed) && step(elapsed) % 2 == 0;
    }

    /** Quique durante o giro, em px para cima: três saltos cada vez mais baixos. */
    static int bounce(long elapsed, int r) {
        if (!spinning(elapsed)) return 0;
        double t = elapsed / (double) SPIN_MS;
        return (int) Math.round(Math.abs(Math.sin(t * Math.PI * 3)) * (1 - t) * Math.max(2, r * 0.22));
    }

    /** Tremida do natural 1 logo depois de pousar, em px para o lado. */
    static int shake(long elapsed) {
        long after = elapsed - SPIN_MS;
        if (after < 0 || after >= SHAKE_MS) return 0;
        double t = after / (double) SHAKE_MS;
        return (int) Math.round(Math.sin(after / 22.0) * 3 * (1 - t));
    }

    /**
     * Escala ao assentar: 1 → ~1,2 → 1 em {@link #POP_MS}. Contínua nas duas pontas (o dado não
     * salta de tamanho ao pousar): sobe rápido e desce desacelerando.
     */
    static float pop(long elapsed) {
        if (elapsed < SPIN_MS) return 1f;
        float t = (elapsed - SPIN_MS) / (float) POP_MS;
        if (t >= 1f) return 1f;
        return 1f + 0.22f * (float) Math.sin(Math.PI * t) * (1f - 0.45f * t);
    }

    /** Entrada do resultado depois de assentar: 0 → 1, desacelerando (ease-out cúbico). */
    static float reveal(long elapsed) {
        long after = elapsed - SPIN_MS - REVEAL_DELAY_MS;
        if (after <= 0) return 0f;
        if (after >= REVEAL_MS) return 1f;
        float t = 1f - after / (float) REVEAL_MS;
        return 1f - t * t * t;
    }

    private static boolean d20(RollResult roll) {
        return !roll.dice().isEmpty() && roll.dice().get(0).sides() == 20;
    }

    /** Crítico de verdade (o motor marcou) ou natural 20 num d20. */
    static boolean critical(RollResult roll) {
        return roll.critical() || (d20(roll) && roll.naturalMax());
    }

    static boolean fumble(RollResult roll) {
        return d20(roll) && roll.naturalOne() && !critical(roll);
    }

    /** Paleta pelo resultado: crítico dourado, falha crítica vermelha, sucesso verde, o resto violeta. */
    static int[] palette(RollResult roll, boolean landed) {
        if (!landed) return VIOLET;
        if (critical(roll)) return GOLD;
        if (fumble(roll)) return RED;
        if (Boolean.TRUE.equals(roll.success())) return GREEN;
        if (Boolean.FALSE.equals(roll.success())) return RED;
        return VIOLET;
    }

    static int[] dim() {
        return DIM;
    }

    /** O d20 descartado pela vantagem/desvantagem, se houver. */
    @Nullable
    static RollResult.Die dropped(RollResult roll) {
        if (roll.dice().size() < 2) return null;
        for (RollResult.Die d : roll.dice()) if (d.dropped() && d.sides() == 20) return d;
        return null;
    }

    /**
     * O dado de uma rolagem inteiro, com centro em ({@code cx}, {@code cy}): girando, pousando, o
     * segundo d20 da vantagem/desvantagem e o floreio do 20 e do 1. Rolagem livre sem d20 mostra o
     * dado do primeiro termo.
     */
    static void drawRoll(GuiGraphics g, Font font, RollResult roll, int cx, int cy, int r, long age, long seed, float alpha) {
        boolean landed = !spinning(age);
        int sides = roll.dice().isEmpty() ? 20 : roll.dice().get(0).sides();
        int natural = roll.natural() > 0 ? roll.natural() : roll.dice().isEmpty() ? roll.total() : roll.dice().get(0).value();

        int dx = landed && fumble(roll) ? shake(age) : 0;
        int dy = -bounce(age, r);
        draw(g, font, cx + dx, cy + dy, r, palette(roll, landed), num(face(age, natural, sides, seed)), pop(age),
                flip(age), alpha);
        if (landed && critical(roll)) sparks(g, cx, cy, r, age - SPIN_MS, alpha);

        // O segundo d20, menor, embaixo à direita e NA FRENTE (atrás, o principal cobria metade do
        // número): gira junto, fora de fase, e ao pousar apaga e leva um risco vermelho.
        RollResult.Die second = dropped(roll);
        if (second != null) {
            int sr = Math.max(6, Math.round(r * 0.48f));
            int sx = cx + Math.round(r * 0.82f), sy = cy + Math.round(r * 0.6f) - (landed ? 0 : bounce(age + 140, sr));
            String n = num(face(age, second.value(), 20, seed * 31 + 7));
            draw(g, font, sx, sy, sr, landed ? DIM : VIOLET, n, 1f, flip(age + 90), alpha * (landed ? 0.8f : 1f));
            if (landed) {
                int half = Math.round(sr * 0.75f);
                g.fill(sx - half, sy, sx + half, sy + 1, FichaUi.alpha(0xFFF87171, alpha * 0.9f));
            }
        }
    }

    /** Faíscas douradas saindo dos vértices, uma vez, ao pousar o crítico. */
    private static void sparks(GuiGraphics g, int cx, int cy, int r, long after, float alpha) {
        if (after < 0 || after >= SPARK_MS) return;
        float t = after / (float) SPARK_MS;
        float ease = 1f - (1f - t) * (1f - t);
        int color = FichaUi.alpha(0xFFFFE9A7, alpha * (1f - t));
        int soft = FichaUi.alpha(0xFFF2C14E, alpha * (1f - t) * 0.8f);
        for (int i = 0; i < 12; i++) {
            double angle = Math.PI / 2 + i * Math.PI / 6;
            boolean vertex = i % 2 == 0;
            double dist = r + (vertex ? 3 + 11 * ease : 2 + 6 * ease);
            int px = cx + (int) Math.round(Math.cos(angle) * dist);
            int py = cy - (int) Math.round(Math.sin(angle) * dist);
            int s = vertex ? 2 : 1;
            g.fill(px - s / 2, py - s / 2, px - s / 2 + s, py - s / 2 + s, vertex ? color : soft);
        }
    }

    /**
     * Desenha o dado com centro em ({@code cx}, {@code cy}) e raio {@code r} (do centro ao vértice).
     * {@code flip} troca o tom das facetas de cima e de baixo (o "giro").
     */
    static void draw(GuiGraphics g, Font font, int cx, int cy, int r, int[] palette, String number, float scale,
                     boolean flip, float alpha) {
        g.pose().pushPose();
        if (scale != 1f) {
            g.pose().translate(cx, cy, 0);
            g.pose().scale(scale, scale, 1);
            g.pose().translate(-cx, -cy, 0);
        }
        int base = FichaUi.alpha(palette[0], alpha);
        int light = FichaUi.alpha(palette[1], alpha);
        int edge = FichaUi.alpha(palette[2], alpha);
        int ink = FichaUi.alpha(palette[3], alpha);
        int upper = flip ? base : mix(base, light, 0.35f);
        int lower = flip ? mix(base, light, 0.35f) : base;

        hexagon(g, cx, cy, r, edge, edge);
        hexagon(g, cx, cy, r - 1, upper, lower);
        // A face da frente: triângulo com o vértice para cima, com borda.
        triangle(g, cx, cy, r, edge, 0);
        triangle(g, cx, cy, r, light, 1);

        float textScale = r >= 14 ? 1f : r >= 10 ? 0.75f : 0.6f;
        if (number.length() >= 3) textScale *= 0.8f;
        float ty = cy + r * 0.12f - 4 * textScale;
        FichaUi.textCenter(g, font, number, cx, ty, ink, textScale, false);
        g.pose().popPose();
    }

    /** Hexágono de vértice para cima, linha a linha. */
    private static void hexagon(GuiGraphics g, int cx, int cy, int r, int upper, int lower) {
        if (r <= 0) return;
        double side = r * 0.866;
        for (int dy = -r; dy < r; dy++) {
            double ady = Math.abs(dy + 0.5);
            double hw = ady <= r / 2.0 ? side : side * (r - ady) / (r / 2.0);
            int half = (int) Math.round(hw);
            if (half <= 0) continue;
            g.fill(cx - half, cy + dy, cx + half, cy + dy + 1, dy < 0 ? upper : lower);
        }
    }

    /** A face triangular; {@code inset} 1 desenha o miolo dentro da borda. */
    private static void triangle(GuiGraphics g, int cx, int cy, int r, int color, int inset) {
        double apex = cy - r * 0.52 + inset;
        double baseY = cy + r * 0.44 - inset;
        double baseHalf = r * 0.64 - inset * 1.4;
        for (int y = (int) Math.ceil(apex); y < (int) Math.floor(baseY); y++) {
            double t = (y + 0.5 - apex) / (baseY - apex);
            int half = (int) Math.round(baseHalf * t);
            if (half <= 0) continue;
            g.fill(cx - half, y, cx + half, y + 1, color);
        }
    }

    private static int mix(int a, int b, float t) {
        int aa = (a >>> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = Math.round(ar + (br - ar) * t), gg = Math.round(ag + (bg - ag) * t), bl = Math.round(ab + (bb - ab) * t);
        return (aa << 24) | (r << 16) | (gg << 8) | bl;
    }
}
