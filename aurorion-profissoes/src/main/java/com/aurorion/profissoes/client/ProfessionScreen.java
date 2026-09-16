package com.aurorion.profissoes.client;

import com.aurorion.core.client.gui.*;
import com.aurorion.profissoes.network.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

/** Texto quebrado e botoes preparados no init/resize, sem medir parágrafos a cada frame. */
public final class ProfessionScreen extends NpcPanelScreen {
    private static final int ROW = 66;
    private static final NpcScreenTheme THEME = new NpcScreenTheme(
            ResourceLocation.parse("aurorion_profissoes:display"),
            new NpcScreenTheme.PanelColors(0x80070C11, 0xD0080D16, 0x85000000, 0xFF57645F,
                    0xF50E191C, 0xF5112025, 0xFF35423F, 0x50334946, 0x28334946, 0x80506961, 0x66000000, 0xFFB6A16F),
            new NpcScreenTheme.TextColors(0xFFE8DCC8, 0xFFF1E8D8, 0xFFACB9B3, 0xFFD1BA83, 0xFFE0A296),
            new NpcScreenTheme.ButtonColors(0xFF213C3D, 0xFF305653, 0xFF182728, 0xFF687B69, 0xFFD1BA83, 0xFFF1E8D8, 0xFF81918B));
    private final PanelPayload data;
    private int scroll, visible;
    private List<FormattedCharSequence> subtitle = List.of();
    private final List<Component> rowTitles = new ArrayList<>();
    private final List<List<FormattedCharSequence>> descriptions = new ArrayList<>();
    private boolean sent;
    public ProfessionScreen(PanelPayload data) {
        super(Component.literal("Ofícios de Aurorion"), Component.literal(data.mode() == 1 ? "PEDIDO DE ATENDIMENTO" : "CONHECIMENTO • CUIDADO • OFÍCIO"), THEME);
        this.data = data;
    }
    @Override protected PanelLayout resolveLayout() {
        int availableWidth = Math.min(420, Math.max(240, width - 24));
        subtitle = font.split(Component.literal(data.title() + " · " + data.subtitle()), availableWidth - 32);
        int header = Math.max(86, 58 + subtitle.size() * 11);
        visible = Math.min(data.rows().size(), Math.max(1, (height - header - 60) / ROW));
        scroll = Mth.clamp(scroll, 0, Math.max(0, data.rows().size() - visible));
        return new PanelLayout(420, header, data.rows().isEmpty() ? 36 : visible * ROW, 36);
    }
    @Override protected void initPanel() {
        rowTitles.clear(); descriptions.clear();
        for (int i = 0; i < visible; i++) {
            var row = data.rows().get(scroll + i);
            rowTitles.add(Component.literal(font.plainSubstrByWidth(row.title(), panelWidth - 130)));
            descriptions.add(font.split(Component.literal(row.detail()), panelWidth - 130));
            var button = panelButton(panelRight() - 98, bodyTop() + i * ROW + 21, 82, 22,
                    Component.literal(data.mode() == 1 ? "Aceitar" : "Escolher"), () -> submit(row.action()));
            button.active = row.enabled() && !sent;
            button.setTooltip(Tooltip.create(Component.literal(row.detail())));
            addRenderableWidget(button);
        }
        addRenderableWidget(panelButton(panelX + 16, footerTop() + 6, 100, 22,
                Component.literal(data.mode() == 1 ? "Recusar" : "Fechar"), this::onClose));
        if (data.mode() == 0) addRenderableWidget(panelButton(panelRight() - 116, footerTop() + 6, 100, 22,
                Component.literal("Atualizar"), () -> submit("refresh")));
    }
    @Override protected void renderPanelBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        for (int i = 0; i < visible; i++) {
            var row = data.rows().get(scroll + i);
            int y = bodyTop() + i * ROW + 2;
            drawRow(graphics, y, ROW - 4, mouseX >= panelX && mouseX < panelRight() && mouseY >= y && mouseY < y + ROW,
                    i % 2 != 0, row.enabled() ? theme.text().accent() : theme.text().muted());
        }
        drawScrollbar(graphics, scroll, visible, data.rows().size());
    }
    @Override protected void renderPanelContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        for (int i = 0; i < subtitle.size(); i++)
            graphics.drawCenteredString(font, subtitle.get(i), panelX + panelWidth / 2, panelY + 46 + i * 11, theme.text().body());
        for (int i = 0; i < visible; i++) {
            int y = bodyTop() + i * ROW;
            graphics.drawString(font, rowTitles.get(i), panelX + 16, y + 9, theme.text().accent(), false);
            var lines = descriptions.get(i);
            for (int j = 0; j < Math.min(3, lines.size()); j++)
                graphics.drawString(font, lines.get(j), panelX + 16, y + 25 + j * 10, theme.text().body(), false);
        }
        if (data.rows().isEmpty()) graphics.drawCenteredString(font, Component.literal(data.mode() == 2 ? "Você pode fechar esta janela." : "Nenhum serviço disponível neste momento."),
                panelX + panelWidth / 2, bodyTop() + 20, theme.text().muted());
    }
    private void submit(String action) {
        if (sent) return;
        sent = true;
        PacketDistributor.sendToServer(new ActionPayload(data.token(), action));
        minecraft.setScreen(null);
    }
    @Override public void onClose() {
        if (!sent && data.mode() != 2) PacketDistributor.sendToServer(new ActionPayload(data.token(), "close"));
        sent = true; super.onClose();
    }
    @Override public boolean mouseScrolled(double x, double y, double dx, double dy) {
        int next = Mth.clamp(scroll - (int)Math.signum(dy), 0, Math.max(0, data.rows().size() - visible));
        if (next != scroll) { scroll = next; rebuildWidgets(); }
        return true;
    }
    @Override public boolean isPauseScreen() { return false; }
}
