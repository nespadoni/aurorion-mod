package com.aurorion.ethereal.client.gui;

import com.aurorion.ethereal.block.entity.AeonicProjectorBlockEntity;
import com.aurorion.ethereal.network.OpenProjectorConfigPayload;
import com.aurorion.ethereal.network.SaveProjectorConfigPayload;
import com.aurorion.ethereal.ranking.BoardMode;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Locale;

/**
 * A tela do Projetor Aeonico: escolher o que projetar e o tamanho do painel.
 *
 * <p>Painel escuro com moldura dourada, uma linha por meta e as setas de tamanho — o mesmo desenho
 * do mod de referencia, com as mesmas cores.
 *
 * <p>A <b>altura do painel e calculada</b> a partir da quantidade de metas, e nao fixa em 268 pixels
 * como no original. La, o layout tinha cinco metas escritas na conta; acrescentar a sexta faria os
 * botoes invadirem a secao de tamanho sem nenhum aviso — e este mod ja tem sete.
 */
public final class ProjectorConfigScreen extends Screen {
    private static final int PANEL_W = 274;
    private static final int HEADER_H = 38;
    private static final int MARGIN = 16;
    private static final int BUTTON_W = PANEL_W - MARGIN * 2;
    private static final int BUTTON_H = 21;
    private static final int BUTTON_GAP = 3;
    private static final int BUTTONS_TOP = 46;
    private static final int ARROW_W = 22;
    private static final int ROW_H = 20;

    private static final int C_OVERLAY = 0xB8060614;
    private static final int C_BG = 0xFF07071A;
    private static final int C_BG_BOTTOM = 0xFF04040F;
    private static final int C_HEADER = 0xFF180838;
    private static final int C_OUTER_BRIGHT = 0xFFD4A017;
    private static final int C_OUTER_DIM = 0xFF7A5A00;
    private static final int C_GOLD_DIM = 0xFF9A7218;
    private static final int C_GRAY = 0xFFAAACC0;
    private static final int C_SELECTED = 0xFF1A2A50;
    private static final int C_SELECTED_BORDER = 0xFF4488CC;
    private static final int C_DIVIDER = 0xFFA07020;
    private static final int C_SECTION = 0xFF0A0A1E;
    private static final int C_SECTION_BORDER = 0xFF2A2A4E;
    private static final int C_CYAN = 0xFF66CCFF;

    private final BlockPos pos;
    private BoardMode selected;
    private float holoWidth;
    private float holoHeight;

    private final int panelH;
    private int px;
    private int py;

    public ProjectorConfigScreen(OpenProjectorConfigPayload payload) {
        super(Component.translatable("gui.aurorion_ethereal.projector.title"));
        this.pos = payload.pos();
        this.selected = payload.boardMode();
        this.holoWidth = payload.holoWidth();
        this.holoHeight = payload.holoHeight();
        this.panelH = confirmY() + ROW_H + 8;
    }

    private static int buttonsBottom() {
        return BUTTONS_TOP + BoardMode.values().length * (BUTTON_H + BUTTON_GAP);
    }

    private static int sectionTop() {
        return buttonsBottom() + 6;
    }

    private static int widthRowY() {
        return sectionTop() + 14;
    }

    private static int heightRowY() {
        return widthRowY() + 26;
    }

    private static int sectionBottom() {
        return heightRowY() + 22;
    }

    private static int confirmY() {
        return sectionBottom() + 8;
    }

    @Override
    protected void init() {
        px = (this.width - PANEL_W) / 2;
        py = (this.height - panelH) / 2;

        BoardMode[] modes = BoardMode.values();
        for (int i = 0; i < modes.length; i++) {
            BoardMode mode = modes[i];
            addRenderableWidget(Button.builder(
                            Component.literal(mode.icon() + "  ").append(mode.displayName()),
                            button -> select(mode))
                    .bounds(px + MARGIN, py + BUTTONS_TOP + i * (BUTTON_H + BUTTON_GAP), BUTTON_W, BUTTON_H)
                    .build());
        }

        int middleW = BUTTON_W - ARROW_W * 2;
        addArrow("◄", px + MARGIN, py + widthRowY(),
                () -> holoWidth = step(holoWidth, -AeonicProjectorBlockEntity.STEP,
                        AeonicProjectorBlockEntity.WIDTH_MIN, AeonicProjectorBlockEntity.WIDTH_MAX));
        addArrow("►", px + MARGIN + ARROW_W + middleW, py + widthRowY(),
                () -> holoWidth = step(holoWidth, AeonicProjectorBlockEntity.STEP,
                        AeonicProjectorBlockEntity.WIDTH_MIN, AeonicProjectorBlockEntity.WIDTH_MAX));
        addArrow("▼", px + MARGIN, py + heightRowY(),
                () -> holoHeight = step(holoHeight, -AeonicProjectorBlockEntity.STEP,
                        AeonicProjectorBlockEntity.HEIGHT_MIN, AeonicProjectorBlockEntity.HEIGHT_MAX));
        addArrow("▲", px + MARGIN + ARROW_W + middleW, py + heightRowY(),
                () -> holoHeight = step(holoHeight, AeonicProjectorBlockEntity.STEP,
                        AeonicProjectorBlockEntity.HEIGHT_MIN, AeonicProjectorBlockEntity.HEIGHT_MAX));

        addRenderableWidget(Button.builder(
                        Component.translatable("gui.aurorion_ethereal.projector.confirm"), button -> confirm())
                .bounds(px + PANEL_W / 2 - 55, py + confirmY(), 110, ROW_H)
                .build());
    }

    private void addArrow(String label, int x, int y, Runnable action) {
        addRenderableWidget(Button.builder(Component.literal(label), button -> {
            action.run();
            // Os rotulos de tamanho sao desenhados no render, entao basta o valor mudar — nao ha
            // widget para reconstruir, e reconstruir de dentro do proprio clique e como se perde o foco.
        }).bounds(x, y, ARROW_W, ROW_H).build());
    }

    private void select(BoardMode mode) {
        selected = mode;
    }

    private static float step(float value, float delta, float min, float max) {
        return Mth.clamp(Math.round((value + delta) * 10.0F) / 10.0F, min, max);
    }

    private void confirm() {
        PacketDistributor.sendToServer(new SaveProjectorConfigPayload(pos, selected.ordinal(), holoWidth, holoHeight));
        onClose();
    }

    /**
     * Os widgets sao desenhados aqui, e nao por {@code super.render}.
     *
     * <p>{@code Screen#render} desenha o fundo <b>antes</b> dos widgets. Chamar ele no fim, depois de
     * montar o painel, faria o fundo do vanilla passar por cima de tudo que este metodo desenhou —
     * moldura, cabecalho e realce da meta escolhida sumiriam.
     */
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(0, 0, this.width, this.height, C_OVERLAY);

        drawFrame(graphics);
        drawHeader(graphics);
        highlightSelected(graphics);
        drawSizeSection(graphics);

        for (Renderable widget : this.renderables) {
            widget.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    private void drawFrame(GuiGraphics graphics) {
        outline(graphics, px - 3, py - 3, PANEL_W + 6, panelH + 6, C_OUTER_DIM);
        outline(graphics, px - 2, py - 2, PANEL_W + 4, panelH + 4, C_OUTER_BRIGHT);
        outline(graphics, px - 1, py - 1, PANEL_W + 2, panelH + 2, C_OUTER_DIM);
        graphics.fillGradient(px, py, px + PANEL_W, py + panelH, C_BG, C_BG_BOTTOM);
    }

    private void drawHeader(GuiGraphics graphics) {
        graphics.fillGradient(px, py, px + PANEL_W, py + HEADER_H, C_HEADER, C_BG);
        graphics.fill(px, py, px + PANEL_W, py + 1, C_OUTER_BRIGHT);
        graphics.fill(px + 6, py + HEADER_H - 2, px + PANEL_W - 6, py + HEADER_H - 1, withAlpha(C_DIVIDER, 120));

        graphics.drawCenteredString(this.font, this.title, px + PANEL_W / 2, py + 6, C_GOLD_DIM);
        graphics.drawCenteredString(this.font,
                Component.translatable("gui.aurorion_ethereal.projector.subtitle"),
                px + PANEL_W / 2, py + 20, C_GRAY);
    }

    private void highlightSelected(GuiGraphics graphics) {
        BoardMode[] modes = BoardMode.values();
        for (int i = 0; i < modes.length; i++) {
            if (modes[i] != selected) {
                continue;
            }
            int y = py + BUTTONS_TOP + i * (BUTTON_H + BUTTON_GAP);
            graphics.fill(px + MARGIN, y, px + MARGIN + BUTTON_W, y + BUTTON_H, C_SELECTED);
            outline(graphics, px + MARGIN, y, BUTTON_W, BUTTON_H, C_SELECTED_BORDER);
        }
    }

    private void drawSizeSection(GuiGraphics graphics) {
        int left = px + 14;
        int sectionW = PANEL_W - 28;
        int top = py + sectionTop();
        int bottom = py + sectionBottom();

        graphics.fill(left, top, left + sectionW, bottom, C_SECTION);
        outline(graphics, left, top, sectionW, bottom - top, C_SECTION_BORDER);
        graphics.drawCenteredString(this.font,
                Component.translatable("gui.aurorion_ethereal.projector.size"),
                left + sectionW / 2, top + 3, C_GOLD_DIM);

        int middleW = BUTTON_W - ARROW_W * 2;
        int centerX = px + MARGIN + ARROW_W + middleW / 2;
        graphics.drawCenteredString(this.font, Component.translatable(
                "gui.aurorion_ethereal.projector.width", format(holoWidth)),
                centerX, py + widthRowY() + 6, C_CYAN);
        graphics.drawCenteredString(this.font, Component.translatable(
                "gui.aurorion_ethereal.projector.height", format(holoHeight)),
                centerX, py + heightRowY() + 6, C_CYAN);
    }

    private static String format(float blocks) {
        return String.format(Locale.ROOT, "%.1f", blocks);
    }

    private static void outline(GuiGraphics graphics, int x, int y, int w, int h, int color) {
        graphics.fill(x, y, x + w, y + 1, color);
        graphics.fill(x, y + h - 1, x + w, y + h, color);
        graphics.fill(x, y, x + 1, y + h, color);
        graphics.fill(x + w - 1, y, x + w, y + h, color);
    }

    private static int withAlpha(int argb, int alpha) {
        return (argb & 0x00FFFFFF) | (Math.min(255, alpha) << 24);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
