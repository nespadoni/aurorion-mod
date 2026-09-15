package com.aurorion.core.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Botao narrativo sem a textura vanilla, compartilhado pelas telas de NPC do ecossistema. */
public final class AurorionButton extends Button {
    private final NpcScreenTheme theme;

    public AurorionButton(int x, int y, int width, int height, Component label,
                          OnPress onPress, NpcScreenTheme theme) {
        super(x, y, width, height, theme.display(label), onPress, DEFAULT_NARRATION);
        this.theme = theme;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        NpcScreenTheme.ButtonColors colors = theme.button();
        boolean highlighted = isHoveredOrFocused();
        int background = !active ? colors.disabledSurface()
                : highlighted ? colors.hoveredSurface() : colors.surface();
        int border = highlighted && active ? colors.hoveredBorder() : colors.border();
        int label = active ? colors.label() : colors.disabledLabel();

        graphics.fill(getX(), getY(), getRight(), getBottom(), border);
        graphics.fill(getX() + 1, getY() + 1, getRight() - 1, getBottom() - 1, background);

        // A linha curta de luz da foco sem transformar o botao em um bloco claro.
        if (highlighted && active) {
            graphics.fill(getX() + 2, getY() + 2, getX() + 4, getBottom() - 2,
                    colors.hoveredBorder());
        }

        renderScrollingString(graphics, Minecraft.getInstance().font, 6, label);
    }
}
