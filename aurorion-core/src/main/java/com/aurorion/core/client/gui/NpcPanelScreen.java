package com.aurorion.core.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Moldura comum das interfaces narrativas de NPC.
 *
 * <p>Subclasses medem o conteudo uma vez em {@link #resolveLayout()} e desenham somente o miolo.
 * Cabecalho, fundo, ornamentos, linhas, etiquetas e rolagem permanecem identicos entre mods.
 */
public abstract class NpcPanelScreen extends Screen {
    private final Component eyebrow;
    protected final NpcScreenTheme theme;
    protected int panelX;
    protected int panelY;
    protected int panelWidth;
    protected int panelHeight;
    protected int headerHeight;
    protected int bodyHeight;
    protected int footerHeight;

    protected NpcPanelScreen(Component title, Component eyebrow, NpcScreenTheme theme) {
        super(title);
        this.eyebrow = eyebrow;
        this.theme = theme;
    }

    @Override
    protected final void init() {
        PanelLayout layout = resolveLayout();
        panelWidth = Math.min(layout.width(), Math.max(240, width - 24));
        headerHeight = layout.headerHeight();
        bodyHeight = layout.bodyHeight();
        footerHeight = layout.footerHeight();
        panelHeight = headerHeight + bodyHeight + footerHeight;
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;
        initPanel();
    }

    protected abstract PanelLayout resolveLayout();

    protected abstract void initPanel();

    protected Component headerSubtitle() {
        return Component.empty();
    }

    protected int headerSubtitleColor() {
        return theme.text().muted();
    }

    protected void renderPanelBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) { }

    protected abstract void renderPanelContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick);

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        NpcScreenTheme.PanelColors colors = theme.panel();
        int right = panelRight();
        int bottom = panelBottom();

        graphics.fillGradient(0, 0, width, height, colors.overlayTop(), colors.overlayBottom());
        graphics.fill(panelX + 4, panelY + 5, right + 4, bottom + 5, colors.shadow());
        graphics.fill(panelX - 1, panelY - 1, right + 1, bottom + 1, colors.border());
        graphics.fillGradient(panelX, panelY, right, bottom, colors.surfaceTop(), colors.surfaceBottom());

        // Quatro cantos curtos formam uma moldura fina sem exigir textura ou nove partes.
        int ornament = 13;
        graphics.fill(panelX, panelY, panelX + ornament, panelY + 2, theme.text().accent());
        graphics.fill(right - ornament, panelY, right, panelY + 2, theme.text().accent());
        graphics.fill(panelX, bottom - 2, panelX + ornament, bottom, theme.text().accent());
        graphics.fill(right - ornament, bottom - 2, right, bottom, theme.text().accent());

        graphics.fill(panelX + 12, bodyTop() - 1, right - 12, bodyTop(), colors.divider());
        graphics.fill(panelX + 12, footerTop(), right - 12, footerTop() + 1, colors.divider());
        renderPanelBackground(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderHeader(graphics);
        renderPanelContent(graphics, mouseX, mouseY, partialTick);
    }

    private void renderHeader(GuiGraphics graphics) {
        int center = panelX + panelWidth / 2;
        graphics.drawCenteredString(font, theme.display(eyebrow), center, panelY + 10,
                theme.text().muted());

        graphics.pose().pushPose();
        graphics.pose().translate(center, panelY + 24, 0);
        graphics.pose().scale(1.18F, 1.18F, 1);
        graphics.drawCenteredString(font, theme.display(title), 0, 0, theme.text().title());
        graphics.pose().popPose();

        Component subtitle = headerSubtitle();
        if (!subtitle.getString().isEmpty()) {
            graphics.drawCenteredString(font, subtitle, center, panelY + 43, headerSubtitleColor());
        }
    }

    protected final AurorionButton panelButton(int x, int y, int width, int height,
                                               Component label, ButtonPress onPress) {
        return new AurorionButton(x, y, width, height, label, button -> onPress.press(), theme);
    }

    protected final void drawRow(GuiGraphics graphics, int y, int height, boolean hovered,
                                 boolean alternate, int accent) {
        NpcScreenTheme.PanelColors colors = theme.panel();
        int left = panelX + 7;
        int right = panelRight() - 7;
        graphics.fill(left, y, right, y + height,
                hovered ? colors.hoveredRow() : alternate ? colors.alternateRow() : colors.row());
        graphics.fill(left, y, left + 2, y + height, accent);
    }

    /** Desenha uma etiqueta encostada em {@code right} e devolve o novo limite a esquerda. */
    protected final int drawTag(GuiGraphics graphics, Component label, int right, int y, int color) {
        int textWidth = font.width(label);
        int left = right - textWidth - 8;
        graphics.fill(left, y, right, y + 13, theme.panel().row());
        graphics.fill(left, y, left + 1, y + 13, color);
        graphics.drawString(font, label, left + 4, y + 3, color, false);
        return left - 4;
    }

    protected final void drawScrollbar(GuiGraphics graphics, int scroll, int visible, int total) {
        if (total <= visible || visible <= 0) return;
        int top = bodyTop() + 3;
        int height = bodyHeight - 6;
        int x = panelRight() - 5;
        graphics.fill(x, top, x + 2, top + height, theme.panel().scrollTrack());
        int thumb = Math.max(12, height * visible / total);
        int travel = height - thumb;
        int offset = travel * scroll / Math.max(1, total - visible);
        graphics.fill(x, top + offset, x + 2, top + offset + thumb, theme.panel().scrollThumb());
    }

    protected final int panelRight() { return panelX + panelWidth; }
    protected final int panelBottom() { return panelY + panelHeight; }
    protected final int bodyTop() { return panelY + headerHeight; }
    protected final int footerTop() { return panelBottom() - footerHeight; }

    public record PanelLayout(int width, int headerHeight, int bodyHeight, int footerHeight) {
        public PanelLayout {
            if (width < 1 || headerHeight < 1 || bodyHeight < 1 || footerHeight < 1) {
                throw new IllegalArgumentException("Panel dimensions must be positive");
            }
        }
    }

    @FunctionalInterface
    protected interface ButtonPress {
        void press();
    }
}
