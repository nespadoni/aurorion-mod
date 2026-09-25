package com.aurorion.core.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/**
 * Neblina desenhada em espaco de tela, depois que o quadro ja esta composto.
 *
 * <p>E o caminho que sobrevive a shader pack (ver {@link ShaderPacks}): nada aqui passa pelo
 * pipeline de iluminacao do Iris, entao a Floresta Negra fecha do mesmo jeito com BSL, com
 * Complementary ou sem shader nenhum.
 *
 * <p>Nao e um degrade chapado por cima da tela — isso pareceria um filtro de cor, e nao neblina. Sao
 * {@value #STEPS} molduras concentricas com alfa caindo pelo quadrado da distancia da borda, mais um
 * veu fraco no quadro inteiro. O olho le isso como "o ar fechou em volta", que e o que a neblina
 * volumetrica faz de verdade. Custo: {@value #STEPS}×4 + 1 retangulos por quadro, sem alocar nada e
 * sem textura.
 *
 * <p>Quem chama e responsavel por desenhar isto <b>abaixo do HUD</b> (uma camada registrada antes de
 * {@code VanillaGuiLayers.CAMERA_OVERLAYS}): neblina por cima da barra de itens e do chat deixaria de
 * ser cenario e viraria estorvo.
 */
public final class ScreenFog {
    private static final int STEPS = 10;
    /** Veu no quadro inteiro: a parte que da a cor do ar. */
    private static final float WASH = .40F;
    /** Fechamento pelas bordas: a parte que da a sensacao de distancia curta. */
    private static final float EDGE = .58F;

    private ScreenFog() {
    }

    /**
     * @param color    cor do ar, {@code 0xRRGGBB}
     * @param strength 0 (nada) a 1 (o mais fechado que a neblina chega). Acima de 1 e cortado: uma
     *                 tela 100% opaca nao e neblina, e sim tirar a pessoa do jogo.
     */
    public static void draw(GuiGraphics graphics, int color, float strength) {
        float weight = Mth.clamp(strength, 0, 1);
        if (weight <= .004F) return;

        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        graphics.fill(0, 0, width, height, argb(color, weight * WASH));

        int bandX = Math.max(1, width / (STEPS * 2));
        int bandY = Math.max(1, height / (STEPS * 2));
        for (int step = 0; step < STEPS; step++) {
            float toCenter = step / (float) STEPS;
            float alpha = weight * (1 - toCenter) * (1 - toCenter) * EDGE;
            if (alpha <= .004F) continue;
            int tint = argb(color, alpha);
            int marginX = Math.round(width * toCenter * .5F);
            int marginY = Math.round(height * toCenter * .5F);
            int right = width - marginX;
            int bottom = height - marginY;
            if (right - marginX <= 2 * bandX || bottom - marginY <= 2 * bandY) break;

            graphics.fill(marginX, marginY, right, marginY + bandY, tint);
            graphics.fill(marginX, bottom - bandY, right, bottom, tint);
            graphics.fill(marginX, marginY + bandY, marginX + bandX, bottom - bandY, tint);
            graphics.fill(right - bandX, marginY + bandY, right, bottom - bandY, tint);
        }
    }

    /**
     * Quanto da neblina de tela desenhar, dado o que o caminho do vanilla ja vai desenhar sozinho.
     *
     * <p>Sem shader, o {@code RenderFog} do vanilla faz o trabalho e a tela fica de fora — as duas
     * juntas escureceriam o dobro. Com shader, a tela e tudo o que ha.
     */
    public static float weightFor(float fogWeight) {
        return ShaderPacks.inUse() ? fogWeight : 0;
    }

    private static int argb(int color, float alpha) {
        return (Mth.clamp((int) (alpha * 255), 0, 255) << 24) | (color & 0xFFFFFF);
    }
}
