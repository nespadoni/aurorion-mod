package com.aurorion.economia.client.gui;

import com.aurorion.core.client.gui.NpcPanelScreen;
import com.aurorion.economia.network.EconomyPayloads;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

public final class EconomyStatusScreen extends NpcPanelScreen {
    private final EconomyPayloads.Status data;
    private List<FormattedCharSequence> lines = List.of();

    public EconomyStatusScreen(EconomyPayloads.Status data) {
        super(Component.literal(data.title()), Component.literal(data.success() ? "CONCLUÍDO" : "AVISO"), EconomyTheme.THEME);
        this.data = data;
    }

    @Override
    protected PanelLayout resolveLayout() {
        lines = font.split(Component.literal(data.message()), 330);
        return new PanelLayout(380, 58, Math.max(64, lines.size() * 12 + 26), 38);
    }

    @Override
    protected void initPanel() {
        addRenderableWidget(panelButton(panelX + panelWidth / 2 - 45, footerTop() + 8, 90, 22,
                Component.literal("Fechar"), this::onClose));
    }

    @Override
    protected void renderPanelContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int y = bodyTop() + 18;
        int color = data.success() ? theme.text().body() : theme.text().warning();
        for (FormattedCharSequence line : lines) {
            graphics.drawCenteredString(font, line, panelX + panelWidth / 2, y, color);
            y += 12;
        }
    }

    @Override public boolean isPauseScreen() { return false; }
}
