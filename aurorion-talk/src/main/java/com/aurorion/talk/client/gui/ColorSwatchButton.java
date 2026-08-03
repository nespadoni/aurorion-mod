package com.aurorion.talk.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Quadradinho de cor clicavel, com uma borda de destaque quando selecionado. */
public class ColorSwatchButton extends Button {
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

        graphics.fill(x0, y0, x1, y1, 0xFF000000 | color);

        if (selected) {
            int border = 0xFFFFFFFF;
            graphics.fill(x0 - 2, y0 - 2, x1 + 2, y0, border);
            graphics.fill(x0 - 2, y1, x1 + 2, y1 + 2, border);
            graphics.fill(x0 - 2, y0, x0, y1, border);
            graphics.fill(x1, y0, x1 + 2, y1, border);
        } else if (isHoveredOrFocused()) {
            int border = 0x80FFFFFF;
            graphics.fill(x0 - 1, y0 - 1, x1 + 1, y0, border);
            graphics.fill(x0 - 1, y1, x1 + 1, y1 + 1, border);
            graphics.fill(x0 - 1, y0, x0, y1, border);
            graphics.fill(x1, y0, x1 + 1, y1, border);
        }
    }
}
