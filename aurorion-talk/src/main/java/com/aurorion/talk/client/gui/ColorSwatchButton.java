package com.aurorion.talk.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * Quadradinho de cor clicavel.
 *
 * <h2>Os cantos cortados</h2>
 *
 * <p>Um quadrado perfeito de {@code #FFFFFF} sobre o fundo escuro da tela e um quadrado perfeito de
 * {@code #09090B} sobre o mesmo fundo tem o mesmo problema em direcoes opostas: um estoura, o outro
 * some. Cortar os quatro cantos em um pixel da a silhueta que os separa do fundo sem precisar de uma
 * borda grossa em volta de cada um — com trinta amostras lado a lado, borda grossa vira grade de
 * xadrez e a cor fica em segundo plano.
 *
 * <p>Tudo e {@code fill}, sem textura: sao seis retangulos por amostra, e a tela nao roda por tick.
 */
public class ColorSwatchButton extends Button {
    private static final int OUTLINE = 0xFF101014;
    private static final int SELECTED = 0xFFFFFFFF;
    private static final int HOVERED = 0x90FFFFFF;

    private final int color;
    private boolean selected;

    public ColorSwatchButton(int x, int y, int size, int color, boolean selected, OnPress onPress) {
        super(x, y, size, size, Component.empty(), onPress, DEFAULT_NARRATION);
        this.color = color;
        this.selected = selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public int color() {
        return color;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x0 = getX();
        int y0 = getY();
        int x1 = x0 + width;
        int y1 = y0 + height;

        // Corpo com os cantos comidos: duas faixas verticais e uma horizontal, e nao um quadrado.
        graphics.fill(x0 + 1, y0, x1 - 1, y1, 0xFF000000 | color);
        graphics.fill(x0, y0 + 1, x0 + 1, y1 - 1, 0xFF000000 | color);
        graphics.fill(x1 - 1, y0 + 1, x1, y1 - 1, 0xFF000000 | color);

        // Contorno escuro constante: e o que impede o branco de se fundir com um fundo claro.
        outline(graphics, x0, y0, x1, y1, OUTLINE);

        if (selected) {
            outline(graphics, x0 - 2, y0 - 2, x1 + 2, y1 + 2, SELECTED);
        } else if (isHoveredOrFocused()) {
            outline(graphics, x0 - 1, y0 - 1, x1 + 1, y1 + 1, HOVERED);
        }
    }

    /** Moldura de um pixel, tambem com os cantos cortados, para acompanhar a forma do corpo. */
    private static void outline(GuiGraphics graphics, int x0, int y0, int x1, int y1, int color) {
        graphics.fill(x0 + 1, y0, x1 - 1, y0 + 1, color);
        graphics.fill(x0 + 1, y1 - 1, x1 - 1, y1, color);
        graphics.fill(x0, y0 + 1, x0 + 1, y1 - 1, color);
        graphics.fill(x1 - 1, y0 + 1, x1, y1 - 1, color);
    }
}
