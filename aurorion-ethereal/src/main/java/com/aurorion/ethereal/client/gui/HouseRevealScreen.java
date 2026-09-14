package com.aurorion.ethereal.client.gui;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.List;
import java.util.random.RandomGenerator;

/**
 * A revelacao: a tela que fecha a cerimonia.
 *
 * <p>Ela e encenada em tempo, e nao em quadros. O original contava {@code tick++} dentro do
 * {@code render}, o que amarrava a duracao da cena ao frame rate — num cliente rodando o modpack
 * inteiro a 30 fps, a revelacao levava o dobro do tempo que no cliente do dono do servidor. Aqui
 * cada fase e um instante em milissegundos, entao a cena dura o mesmo para todo mundo.
 *
 * <p>Nenhuma alocacao por frame: as faiscas sao arrays de {@code float} preenchidos no {@code init}
 * e reciclados no lugar; a quebra de linha do lema e feita uma vez (SDD §4.2).
 */
public class HouseRevealScreen extends Screen {
    private static final long PHASE_RING = 700;
    private static final long PHASE_NAME = 1_600;
    private static final long PHASE_MOTTO = 2_600;
    private static final long PHASE_BUTTON = 3_600;
    private static final long FADE = 700;

    private static final int SPARKS = 48;
    private static final int RING_MARKS = 24;
    private static final int RING_RADIUS = 78;

    private static final int COLOR_INTRO = 0xFFCFCFE4;
    private static final int COLOR_LEAD = 0xFFFFFFFF;

    /** Teto do passo de tempo: uma pausa longa (janela minimizada) nao teleporta as faiscas. */
    private static final float MAX_STEP = 0.05F;

    private final Component houseName;
    private final Component motto;
    private final int color;
    private final int argb;

    private final float[] sparkX = new float[SPARKS];
    private final float[] sparkY = new float[SPARKS];
    private final float[] sparkVx = new float[SPARKS];
    private final float[] sparkVy = new float[SPARKS];
    private final float[] sparkLife = new float[SPARKS];
    private final float[] sparkDecay = new float[SPARKS];

    private List<FormattedCharSequence> wrappedMotto = List.of();

    /**
     * Um gerador so, guardado.
     *
     * <p>{@code RandomGenerator.getDefault()} constroi uma instancia nova a cada chamada — pedir um
     * dentro do laco de faiscas seria uma alocacao por frame, exatamente o que esta cena diz nao
     * fazer.
     */
    private final RandomGenerator random = RandomGenerator.getDefault();

    private long startedAt;
    private long lastFrame;
    private boolean buttonAdded;

    public HouseRevealScreen(Component houseName, Component motto, int color) {
        super(Component.translatable("gui.aurorion_ethereal.reveal.title"));
        this.houseName = houseName;
        this.motto = motto;
        this.color = color;
        this.argb = 0xFF000000 | color;
    }

    /**
     * {@code init} roda de novo a cada redimensionamento da janela, entao o relogio da cena so pode
     * ser zerado na primeira vez.
     *
     * <p>Zerando sempre, esticar a janela no meio da revelacao voltaria a cena para o comeco — e como
     * ela nao fecha no Esc, o jogador ficaria preso mais alguns segundos esperando o botao renascer.
     */
    @Override
    protected void init() {
        if (startedAt == 0L) {
            startedAt = Util.getMillis();
            lastFrame = startedAt;
        }
        // O botao e um widget, e os widgets foram descartados no redimensionamento: ele precisa ser
        // recriado, e o render cuida disso na proxima passada.
        buttonAdded = false;

        wrappedMotto = this.font.split(motto, Math.min(360, this.width - 40));

        for (int i = 0; i < SPARKS; i++) {
            resetSpark(i, true);
        }
    }

    private void resetSpark(int i, boolean scattered) {
        sparkX[i] = this.width / 2.0F + (scattered ? random.nextFloat() * 40.0F - 20.0F : 0.0F);
        sparkY[i] = this.height / 2.0F + (scattered ? random.nextFloat() * 30.0F - 15.0F : 0.0F);

        float angle = random.nextFloat() * Mth.TWO_PI;
        float speed = 40.0F + random.nextFloat() * 110.0F;
        sparkVx[i] = Mth.cos(angle) * speed;
        sparkVy[i] = Mth.sin(angle) * speed;
        sparkLife[i] = 1.0F;
        sparkDecay[i] = 0.35F + random.nextFloat() * 0.55F;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        long now = Util.getMillis();
        float step = Math.min((now - lastFrame) / 1000.0F, MAX_STEP);
        lastFrame = now;
        long elapsed = now - startedAt;

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        graphics.fill(0, 0, this.width, this.height, 0xFF000000);
        drawGlow(graphics, centerX, centerY);
        updateAndDrawSparks(graphics, step);

        if (elapsed > PHASE_RING) {
            drawRing(graphics, centerX, centerY, fade(elapsed - PHASE_RING), elapsed);
        }

        if (elapsed > PHASE_NAME) {
            float progress = fade(elapsed - PHASE_NAME);
            int alpha = (int) (progress * 255.0F);
            int slide = (int) ((1.0F - progress) * -30.0F);

            graphics.drawCenteredString(this.font,
                    Component.translatable("gui.aurorion_ethereal.reveal.recognized"),
                    centerX, centerY + 26 + slide, withAlpha(COLOR_INTRO, (int) (progress * 170.0F)));
            graphics.drawCenteredString(this.font,
                    Component.translatable("gui.aurorion_ethereal.reveal.you_are"),
                    centerX, centerY + 42 + slide, withAlpha(COLOR_LEAD, alpha));
            drawScaled(graphics, houseName, centerX, centerY + 56 + slide, 2.0F, withAlpha(argb, alpha));
        }

        if (elapsed > PHASE_MOTTO) {
            int alpha = (int) (fade(elapsed - PHASE_MOTTO) * 200.0F);
            int y = centerY + 96;
            for (FormattedCharSequence line : wrappedMotto) {
                graphics.drawString(this.font, line, centerX - this.font.width(line) / 2, y,
                        withAlpha(COLOR_INTRO, alpha), false);
                y += 11;
            }
        }

        if (elapsed > PHASE_BUTTON && !buttonAdded) {
            buttonAdded = true;
            addRenderableWidget(Button.builder(
                            Component.translatable("gui.aurorion_ethereal.reveal.accept"), button -> onClose())
                    .bounds(centerX - 100, centerY + 128, 200, 20)
                    .build());
        }

        // O botao e desenhado aqui, e nao por super.render: Screen#render pinta o fundo do vanilla
        // antes dos widgets, e ele apagaria a cena inteira. Esta tela ja tem o proprio fundo opaco.
        for (Renderable widget : this.renderables) {
            widget.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    /** Halo na cor da casa: retangulos concentricos, do mais fraco para o mais forte. */
    private void drawGlow(GuiGraphics graphics, int centerX, int centerY) {
        for (int i = 6; i >= 1; i--) {
            int radius = i * 26;
            graphics.fill(centerX - radius, centerY - radius / 2, centerX + radius, centerY + radius / 2,
                    withAlpha(argb, 10));
        }
    }

    /** Anel de marcas girando devagar em volta do centro. */
    private void drawRing(GuiGraphics graphics, int centerX, int centerY, float progress, long elapsed) {
        float spin = elapsed / 2600.0F;
        int alpha = (int) (progress * 220.0F);
        int radius = (int) (RING_RADIUS * (0.6F + 0.4F * progress));

        for (int i = 0; i < RING_MARKS; i++) {
            float angle = Mth.TWO_PI * i / RING_MARKS + spin;
            int x = centerX + (int) (Mth.cos(angle) * radius);
            int y = centerY + (int) (Mth.sin(angle) * radius * 0.55F);
            // Marcas alternadas em tamanho: da ao anel um ritmo, em vez de um pontilhado uniforme.
            int size = (i % 2 == 0) ? 3 : 2;
            graphics.fill(x - size, y - size, x + size, y + size, withAlpha(argb, alpha));
        }
    }

    private void updateAndDrawSparks(GuiGraphics graphics, float step) {
        for (int i = 0; i < SPARKS; i++) {
            sparkX[i] += sparkVx[i] * step;
            sparkY[i] += sparkVy[i] * step;
            sparkLife[i] -= sparkDecay[i] * step;

            if (sparkLife[i] <= 0.0F) {
                resetSpark(i, false);
                continue;
            }

            int alpha = (int) (sparkLife[i] * 200.0F);
            int x = (int) sparkX[i];
            int y = (int) sparkY[i];
            graphics.fill(x, y, x + 2, y + 2, withAlpha(argb, alpha));
        }
    }

    private void drawScaled(GuiGraphics graphics, Component text, int x, int y, float scale, int color) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(this.font, text, -this.font.width(text) / 2, 0, color, true);
        graphics.pose().popPose();
    }

    private static float fade(long since) {
        return Mth.clamp(since / (float) FADE, 0.0F, 1.0F);
    }

    private static int withAlpha(int argb, int alpha) {
        return (Mth.clamp(alpha, 0, 255) << 24) | (argb & 0x00FFFFFF);
    }

    /**
     * A cena nao pode ser pulada com Esc: e o unico momento em que a casa aparece, e sair dela por
     * engano deixaria o jogador sem ter visto o proprio resultado. O botao do fim fecha.
     */
    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
