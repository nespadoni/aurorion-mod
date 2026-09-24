package com.aurorion.magia.client;

import com.aurorion.magia.AurorionMagia;
import com.aurorion.magia.network.SpellVisualPayload.Kind;
import com.aurorion.magia.registry.MagiaEffects;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

/**
 * A tela de quem esta sendo controlado. Cada magia de dominio tem a sua "possessao":
 *
 * <ul>
 *   <li><b>Imperium Mentis</b> — veu jade-prateado que respira, fios de marionete descendo do alto da
 *       tela e ordens em latim surgindo e sumindo ("OBOEDI", "MEUS ES"...). A camera balanca e o
 *       campo de visao pulsa ({@code MagiaClientEvents}).</li>
 *   <li><b>Dolor Cruciatus / Dolor Universus</b> — vinheta de sangue batendo como coracao e veias
 *       rachando das bordas para dentro, crescendo quanto mais a dor dura.</li>
 *   <li><b>Aspectus Captus</b> — tunel escuro: so um circulo no centro continua visivel, e o campo de
 *       visao aperta, prendendo o olhar em quem conjurou.</li>
 *   <li><b>Genua Flecte</b> — peso descendo do alto da tela e sombra lilas nas bordas.</li>
 *   <li><b>Vox Interdicta</b> — faixa negra subindo do pe da tela, como uma mao sobre a boca.</li>
 *   <li><b>Mao do Algoz</b> — bordas violeta apertando enquanto voce e segurado.</li>
 * </ul>
 *
 * <p>Tudo e retangulo e texto em cima do HUD: sem shader, entao funciona com qualquer pacote de
 * shaders do pack. So desenha quando algum desses estados esta ativo; fora disso, sai na primeira
 * linha. Nao respeita F1 de proposito — ninguem escapa da possessao escondendo o HUD.
 */
public final class PossessionLayer implements LayeredDraw.Layer {
    public static final ResourceLocation ID = AurorionMagia.id("possessao");

    private static final int FADE_TICKS = 20;
    private static final String[] WHISPERS = {"OBOEDI", "MEUS ES", "SERVI", "NON ES TUUS", "GENUFLECTE", "TACE"};
    /** Veias: polilinhas em coordenadas 0..1 da tela, geradas uma vez, sempre as mesmas. */
    private static final float[][] VEINS = veins();

    /** Tick em que a dor comecou; -1 sem dor. As veias crescem a partir dele. */
    private static int painSince = -1;

    @Override
    public void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) return;

        float time = player.tickCount + delta.getGameTimeDeltaPartialTick(false);
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();

        float pain = weight(player, MagiaEffects.CRUCIATUS);
        if (pain > 0) {
            if (painSince < 0) painSince = player.tickCount;
            pain(graphics, width, height, time, pain, (player.tickCount - painSince) / 60f);
        } else {
            painSince = -1;
        }

        float control = weight(player, MagiaEffects.DISORIENTED);
        if (control > 0) imperium(graphics, minecraft.font, width, height, time, control);

        float kneel = weight(player, MagiaEffects.KNEELING);
        if (kneel > 0) kneel(graphics, width, height, time, kneel);

        float silence = weight(player, MagiaEffects.SILENCED);
        if (silence > 0) silence(graphics, width, height, time, silence);

        float captive = weight(player, MagiaEffects.CAPTIVE);
        if (captive > 0) tunnel(graphics, width, height, time, captive);

        float alone = weight(player, MagiaEffects.SOLITARY);
        if (alone > 0) solitude(graphics, width, height, time, alone);

        if (ClientSpellVisuals.targets(Kind.MANUS_GRIP, player.getId())) {
            vignette(graphics, width, height, 0x2A0E4A, .55F, .22F);
        }
    }

    /** 0 sem o efeito; 1 com ele; some nos ultimos segundos em vez de cortar. */
    private static float weight(LocalPlayer player, Holder<MobEffect> effect) {
        MobEffectInstance instance = player.getEffect(effect);
        if (instance == null) return 0;
        if (instance.isInfiniteDuration()) return 1;
        return Mth.clamp(instance.getDuration() / (float) FADE_TICKS, 0, 1);
    }

    // --- Cada possessao ----------------------------------------------------------------------------

    private static void pain(GuiGraphics g, int w, int h, float time, float weight, float grown) {
        // Batida de coracao: pico curto a cada pulso da canalizacao (10 ticks), quase nada entre eles.
        float beat = (float) Math.pow(Math.max(0, Mth.sin(time * Mth.TWO_PI / 10f)), 6);
        vignette(g, w, h, 0x5A0008, weight * (.35F + .4F * beat), .28F);
        veins(g, w, h, Mth.clamp(grown, .15F, 1), 0x8A0010, weight * (.55F + .35F * beat));
        if (beat > .6F) g.fill(0, 0, w, h, argb(0x7A0000, .12F * beat * weight));
    }

    private static void imperium(GuiGraphics g, Font font, int w, int h, float time, float weight) {
        float breath = .85F + .15F * Mth.sin(time * .12F);
        g.fill(0, 0, w, h, argb(0x06140F, .22F * weight * breath));
        vignette(g, w, h, 0x0F3A2A, .6F * weight * breath, .3F);
        strings(g, w, h, time, weight);
        whisper(g, font, w, h, time, weight);
    }

    private static void kneel(GuiGraphics g, int w, int h, float time, float weight) {
        // Algo pesado apertando de cima para baixo.
        int band = (int) (h * (.45F + .05F * Mth.sin(time * .08F)));
        g.fillGradient(0, 0, w, band, argb(0x0E0618, .7F * weight), argb(0x0E0618, 0));
        vignette(g, w, h, 0x3A2458, .45F * weight, .2F);
    }

    private static void silence(GuiGraphics g, int w, int h, float time, float weight) {
        int band = (int) (h * .32F);
        g.fillGradient(0, h - band, w, h, argb(0x050108, 0), argb(0x050108, .85F * weight));
        vignette(g, w, h, 0x1B0A24, .35F * weight, .18F);
        // Costura: pontos atravessando a faixa, como uma boca fechada.
        int y = h - band / 3;
        int stitches = 9;
        int span = w / 3;
        int x0 = (w - span) / 2;
        int color = argb(0x4A2A5A, .8F * weight);
        for (int i = 0; i < stitches; i++) {
            int x = x0 + span * i / (stitches - 1);
            g.fill(x - 1, y - 4, x + 1, y + 4, color);
        }
        g.fill(x0, y - 1, x0 + span, y, argb(0x2A1236, .7F * weight));
    }

    /**
     * Mundo Vazio: nada de veu forte. A tela quase nao muda — e esse o susto.
     *
     * <p>So uma sombra fria nas bordas e um chiado de riscos horizontais, fraco e irregular, como um
     * sinal ruim. A pessoa nao pode ter certeza de que levou magia: ela ve o corredor vazio e conclui
     * sozinha que os outros foram embora. Um efeito de tela grande entregaria a mentira na hora.
     */
    private static void solitude(GuiGraphics g, int w, int h, float time, float weight) {
        vignette(g, w, h, 0x05030C, .55F * weight, .26F);
        int lines = 5;
        for (int i = 0; i < lines; i++) {
            // Passo primo por linha: os riscos nunca reaparecem no mesmo ritmo.
            float phase = time * (.013F + .004F * i) + i * 7.3F;
            int y = (int) ((phase - Math.floor(phase)) * h);
            float alpha = .05F * weight * (1 - i / (float) lines);
            g.fill(0, y, w, y + 1, argb(0x2A1840, alpha));
        }
    }

    /** Tunel: so um circulo no centro fica aberto; o resto escurece em duas camadas suaves. */
    private static void tunnel(GuiGraphics g, int w, int h, float time, float weight) {
        float cx = w / 2f, cy = h / 2f;
        float radius = Math.min(w, h) * (.36F + .02F * Mth.sin(time * .2F));
        mask(g, w, h, cx, cy, radius * 1.18F, argb(0x07020C, .55F * weight));
        mask(g, w, h, cx, cy, radius, argb(0x07020C, .4F * weight));
        vignette(g, w, h, 0x2B0D3F, .5F * weight, .12F);
    }

    // --- Primitivas --------------------------------------------------------------------------------

    /** Vinheta em faixas: forte na borda, apagando para dentro. 4 retangulos por faixa. */
    private static void vignette(GuiGraphics g, int w, int h, int rgb, float alpha, float depth) {
        int bands = 8;
        int step = Math.max(1, (int) (Math.min(w, h) * depth / bands));
        for (int i = 0; i < bands; i++) {
            float falloff = 1 - i / (float) bands;
            int color = argb(rgb, alpha * falloff * falloff);
            int in = i * step;
            g.fill(in, in, w - in, in + step, color);
            g.fill(in, h - in - step, w - in, h - in, color);
            g.fill(in, in + step, in + step, h - in - step, color);
            g.fill(w - in - step, in + step, w - in, h - in - step, color);
        }
    }

    /** Escurece tudo fora do circulo, linha a linha. */
    private static void mask(GuiGraphics g, int w, int h, float cx, float cy, float radius, int color) {
        int row = 2;
        for (int y = 0; y < h; y += row) {
            float dy = y + row / 2f - cy;
            if (Math.abs(dy) >= radius) {
                g.fill(0, y, w, y + row, color);
                continue;
            }
            int half = (int) Math.sqrt(radius * radius - dy * dy);
            g.fill(0, y, (int) cx - half, y + row, color);
            g.fill((int) cx + half, y, w, y + row, color);
        }
    }

    /** Fios de marionete balancando do alto da tela. */
    private static void strings(GuiGraphics g, int w, int h, float time, float weight) {
        int color = argb(0xC8E6DA, .35F * weight);
        for (int s = 0; s < 5; s++) {
            float x = w * (.18F + .16F * s);
            float length = h * (.3F + .08F * Mth.sin(time * .05F + s));
            int segments = 14;
            float previous = x;
            for (int i = 0; i < segments; i++) {
                float t = (i + 1) / (float) segments;
                float sway = Mth.sin(time * .07F + s * 1.3F) * 10 * t;
                float current = x + sway;
                int y0 = (int) (length * i / segments);
                int y1 = (int) (length * t);
                int left = (int) Math.min(previous, current);
                g.fill(left, y0, left + 1, y1, color);
                previous = current;
            }
            // A cruz do manipulador, la no alto.
            g.fill((int) x - 4, 2, (int) x + 5, 3, color);
        }
    }

    /** Uma ordem por vez, em lugar sorteado, aparecendo e sumindo devagar. */
    private static void whisper(GuiGraphics g, Font font, int w, int h, float time, float weight) {
        int window = 50;
        int index = (int) (time / window);
        float phase = (time % window) / window;
        float alpha = Mth.sin(phase * Mth.PI) * .55F * weight;
        if (alpha < .04F) return;
        // Hash inteiro da janela: mesma palavra e lugar durante ela, sem alocar gerador por quadro.
        long seed = mix(index);
        String word = WHISPERS[(int) Math.floorMod(seed, WHISPERS.length)];
        float scale = 2F;
        int x = (int) ((w * (.15F + .7F * unit(seed >>> 16))) / scale);
        int y = (int) ((h * (.2F + .6F * unit(seed >>> 40))) / scale);
        g.pose().pushPose();
        g.pose().scale(scale, scale, 1);
        g.drawString(font, word, x - font.width(word) / 2, y, argb(0xB8F0D8, Math.max(.05F, alpha)), false);
        g.pose().popPose();
    }

    /** Veias crescendo das bordas; {@code grown} de 0 a 1 diz quanto de cada uma ja apareceu. */
    private static void veins(GuiGraphics g, int w, int h, float grown, int rgb, float alpha) {
        int color = argb(rgb, alpha);
        for (float[] vein : VEINS) {
            int points = vein.length / 2;
            int visible = Math.max(2, (int) (points * grown));
            for (int i = 1; i < visible; i++) {
                float x0 = vein[(i - 1) * 2] * w, y0 = vein[(i - 1) * 2 + 1] * h;
                float x1 = vein[i * 2] * w, y1 = vein[i * 2 + 1] * h;
                int thickness = Math.max(1, 3 - i * 3 / points);
                line(g, x0, y0, x1, y1, thickness, color);
            }
        }
    }

    private static void line(GuiGraphics g, float x0, float y0, float x1, float y1, int thickness, int color) {
        int steps = (int) Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0)) / 2 + 1;
        for (int i = 0; i <= steps; i++) {
            float t = i / (float) steps;
            int x = (int) (x0 + (x1 - x0) * t);
            int y = (int) (y0 + (y1 - y0) * t);
            g.fill(x, y, x + thickness, y + thickness, color);
        }
    }

    /** Dez veias, cada uma nascendo numa borda e rachando para o centro em zigue-zague. */
    private static float[][] veins() {
        RandomSource random = RandomSource.create(20260923L);
        float[][] veins = new float[10][];
        for (int v = 0; v < veins.length; v++) {
            int points = 12;
            float[] vein = new float[points * 2];
            int side = v % 4;
            float x = side == 0 ? 0 : side == 1 ? 1 : random.nextFloat();
            float y = side == 2 ? 0 : side == 3 ? 1 : random.nextFloat();
            float dx = .5F - x, dy = .5F - y;
            float length = Mth.sqrt(dx * dx + dy * dy);
            dx /= length;
            dy /= length;
            for (int i = 0; i < points; i++) {
                vein[i * 2] = x;
                vein[i * 2 + 1] = y;
                float jitter = (random.nextFloat() - .5F) * .9F;
                x += (dx - dy * jitter) * .025F;
                y += (dy + dx * jitter) * .025F;
            }
            veins[v] = vein;
        }
        return veins;
    }

    /** SplitMix64: espalha o indice da janela em bits bem misturados. */
    private static long mix(long value) {
        long z = value * 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** 0..1 a partir de 16 bits do hash. */
    private static float unit(long bits) {
        return (bits & 0xFFFF) / 65535f;
    }

    private static int argb(int rgb, float alpha) {
        return Mth.clamp(Math.round(alpha * 255), 0, 255) << 24 | rgb & 0xFFFFFF;
    }
}
