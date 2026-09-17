package com.aurorion.ethereal.client.gui;

import com.aurorion.ethereal.network.HouseMuralPayloads;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** Escolha em duas etapas de quem receberá a vida; o servidor valida novamente na confirmação. */
public final class ProtectorTargetScreen extends Screen {
    private static final int PANEL_W = 386;
    private static final int HEADER_H = 51;
    private static final int ROW_H = 34;
    private static final int FOOTER_H = 40;
    private static final int MAX_VISIBLE = 7;
    private static final int C_PANEL = 0xFF050205;
    private static final int C_ROW = 0xFF100A10;
    private static final int C_ALT = 0xFF160B12;
    private static final int C_SELECTED = 0xFF2A0B16;
    private static final int C_RED = 0xFF9A2D44;
    private static final int C_TEXT = 0xFFE6DDD9;
    private static final int C_MUTED = 0xFF867980;

    private final HouseMuralPayloads.OpenTargets data;
    private int left;
    private int top;
    private int visible;
    private int scroll;
    @Nullable private UUID selected;

    public ProtectorTargetScreen(HouseMuralPayloads.OpenTargets data) {
        super(Component.translatable("gui.aurorion_ethereal.mural.protector.targets"));
        this.data = data;
    }

    @Override
    protected void init() {
        visible = Math.min(MAX_VISIBLE, Math.max(1,
                Math.min(data.targets().size(), (height - HEADER_H - FOOTER_H - 30) / ROW_H)));
        scroll = Mth.clamp(scroll, 0, Math.max(0, data.targets().size() - visible));
        int panelH = HEADER_H + Math.max(ROW_H, visible * ROW_H) + FOOTER_H;
        left = (width - PANEL_W) / 2;
        top = (height - panelH) / 2;

        for (int i = 0; i < visible && scroll + i < data.targets().size(); i++) {
            HouseMuralPayloads.Target target = data.targets().get(scroll + i);
            boolean chosen = target.id().equals(selected);
            Component label = Component.translatable(chosen
                    ? "gui.aurorion_ethereal.mural.protector.confirm"
                    : "gui.aurorion_ethereal.mural.protector.choose");
            Button button = addRenderableWidget(Button.builder(label, ignored -> choose(target))
                    .bounds(left + PANEL_W - 104, top + HEADER_H + i * ROW_H + 7, 88, 20).build());
            button.active = target.lives() < target.maxLives();
            if (!button.active) button.setTooltip(Tooltip.create(Component.translatable(
                    "gui.aurorion_ethereal.mural.protector.full_lives")));
        }

        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, ignored -> onClose())
                .bounds(left + PANEL_W / 2 - 55, top + panelH - 29, 110, 20).build());
    }

    private void choose(HouseMuralPayloads.Target target) {
        if (!target.id().equals(selected)) {
            selected = target.id();
            rebuildWidgets();
            return;
        }
        PacketDistributor.sendToServer(new HouseMuralPayloads.GrantLife(data.pos(), target.id()));
        onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        int rowsHeight = Math.max(ROW_H, visible * ROW_H);
        int panelH = HEADER_H + rowsHeight + FOOTER_H;
        graphics.fill(0, 0, width, height, 0xD4000000);
        graphics.fill(left - 2, top - 2, left + PANEL_W + 2, top + panelH + 2, C_RED);
        graphics.fill(left, top, left + PANEL_W, top + panelH, C_PANEL);
        graphics.drawCenteredString(font, title, left + PANEL_W / 2, top + 9, C_RED);
        graphics.drawCenteredString(font, data.houseName(), left + PANEL_W / 2, top + 25, C_MUTED);
        graphics.drawCenteredString(font, Component.translatable(
                "gui.aurorion_ethereal.mural.protector.warning"), left + PANEL_W / 2, top + 38, C_MUTED);

        if (data.targets().isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable(
                    "gui.aurorion_ethereal.mural.protector.no_targets"),
                    left + PANEL_W / 2, top + HEADER_H + 12, C_MUTED);
        }
        for (int i = 0; i < visible && scroll + i < data.targets().size(); i++) {
            HouseMuralPayloads.Target target = data.targets().get(scroll + i);
            int y = top + HEADER_H + i * ROW_H;
            graphics.fill(left + 8, y + 3, left + PANEL_W - 8, y + ROW_H - 3,
                    target.id().equals(selected) ? C_SELECTED : i % 2 == 0 ? C_ROW : C_ALT);
            graphics.fill(left + 8, y + 3, left + 11, y + ROW_H - 3, C_RED);
            graphics.drawString(font, target.name(), left + 18, y + 7, C_TEXT, false);
            Component status = target.lives() <= 0
                    ? Component.translatable("gui.aurorion_ethereal.mural.protector.limbo")
                    : Component.translatable("gui.aurorion_ethereal.mural.protector.lives",
                            target.lives(), target.maxLives());
            graphics.drawString(font, status, left + 18, y + 20,
                    target.lives() <= 0 ? C_RED : C_MUTED, false);
            if (!target.online()) {
                graphics.drawString(font, Component.translatable("gui.aurorion_ethereal.mural.protector.offline"),
                        left + 210, y + 12, C_MUTED, false);
            }
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        int next = Mth.clamp(scroll - (int) Math.signum(deltaY), 0,
                Math.max(0, data.targets().size() - visible));
        if (next != scroll) {
            scroll = next;
            selected = null;
            rebuildWidgets();
        }
        return true;
    }

    @Override public boolean isPauseScreen() { return false; }
}
