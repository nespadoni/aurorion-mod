package com.aurorion.economia.client.gui;

import com.aurorion.core.client.gui.NpcPanelScreen;
import com.aurorion.economia.network.EconomyPayloads;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

/** Menu extensivel do Shift+G. As opcoes chegam autorizadas pelo servidor. */
public final class InteractionMenuScreen extends NpcPanelScreen {
    private static final int ROW_HEIGHT = 58;
    private final EconomyPayloads.OpenMenu data;
    private int scroll;
    private int visible;

    public InteractionMenuScreen(EconomyPayloads.OpenMenu data) {
        super(Component.literal(data.targetName()), Component.literal("INTERAÇÃO"), EconomyTheme.THEME);
        this.data = data;
    }

    @Override
    protected PanelLayout resolveLayout() {
        int room = Math.max(1, (height - 62 - 38 - 36) / ROW_HEIGHT);
        visible = Math.min(data.options().size(), room);
        scroll = Mth.clamp(scroll, 0, Math.max(0, data.options().size() - visible));
        return new PanelLayout(390, 62, data.options().isEmpty() ? 68 : visible * ROW_HEIGHT, 38);
    }

    @Override
    protected Component headerSubtitle() {
        return Component.literal("Escolha o que deseja propor");
    }

    @Override
    protected void initPanel() {
        for (int i = 0; i < visible; i++) {
            EconomyPayloads.OpenMenu.Option option = optionAt(i);
            var button = panelButton(panelRight() - 112, bodyTop() + i * ROW_HEIGHT + 19, 92, 22,
                    Component.literal("Selecionar"), () -> select(option.action()));
            button.active = option.enabled();
            button.setTooltip(Tooltip.create(Component.literal(option.detail())));
            addRenderableWidget(button);
        }
        addRenderableWidget(panelButton(panelX + 16, footerTop() + 8, 86, 22,
                Component.literal("Fechar"), this::onClose));
    }

    @Override
    protected void renderPanelBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        for (int i = 0; i < visible; i++) {
            int y = bodyTop() + i * ROW_HEIGHT + 3;
            drawRow(graphics, y, ROW_HEIGHT - 6,
                    mouseX >= panelX && mouseX < panelRight() && mouseY >= y && mouseY < y + ROW_HEIGHT,
                    i % 2 != 0, theme.text().accent());
        }
        drawScrollbar(graphics, scroll, visible, data.options().size());
    }

    @Override
    protected void renderPanelContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        for (int i = 0; i < visible; i++) {
            EconomyPayloads.OpenMenu.Option option = optionAt(i);
            int y = bodyTop() + i * ROW_HEIGHT;
            graphics.drawString(font, Component.literal(option.title()), panelX + 18, y + 13,
                    option.enabled() ? theme.text().accent() : theme.text().muted(), false);
            graphics.drawString(font, Component.literal(font.plainSubstrByWidth(option.detail(), panelWidth - 158)),
                    panelX + 18, y + 31, theme.text().body(), false);
        }
    }

    private void select(String action) {
        PacketDistributor.sendToServer(new EconomyPayloads.MenuAction(data.target(), action));
        minecraft.setScreen(null);
    }

    private EconomyPayloads.OpenMenu.Option optionAt(int index) {
        return data.options().get(scroll + index);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        int next = Mth.clamp(scroll - (int)Math.signum(deltaY), 0,
                Math.max(0, data.options().size() - visible));
        if (next != scroll) {
            scroll = next;
            // Cada botao captura a acao da linha. Recriar impede selecionar uma opcao que saiu da tela.
            rebuildWidgets();
        }
        return true;
    }

    @Override public boolean isPauseScreen() { return false; }
}
