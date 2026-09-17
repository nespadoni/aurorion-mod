package com.aurorion.economia.client.gui;

import com.aurorion.core.client.gui.NpcPanelScreen;
import com.aurorion.economia.config.EconomyConfig;
import com.aurorion.economia.money.Money;
import com.aurorion.economia.network.LandPayloads;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.List;

public final class LandApprovalScreen extends NpcPanelScreen {
    private final LandPayloads.Approval data;
    private final List<String> lines;
    private boolean answered;
    public LandApprovalScreen(LandPayloads.Approval data) {
        super(Component.literal("Comprar terreno?"), Component.literal("ESCRITURA DO PERSONAGEM"), EconomyTheme.THEME);
        this.data = data;
        lines = List.of("Corretor: " + data.broker(), "Proprietário: " + data.buyer(),
                data.width() + " × " + data.length() + " = " + ((long) data.width() * data.length()) + " m² • " + EconomyConfig.ZONE_NAMES[data.zone()],
                "De " + data.x() + ", " + data.z() + " até " + (data.x() + data.width() - 1) + ", " + (data.z() + data.length() - 1),
                "Dimensão: " + data.dimension(), "Pagamento: " + Money.describe(data.price()),
                "Sua carteira: " + Money.describe(data.balance()), "Destino: sistema (dinheiro retirado de circulação).",
                "Não será herdado pelo seu próximo personagem.");
    }
    @Override protected PanelLayout resolveLayout() { return new PanelLayout(420, 58, 148, 36); }
    @Override protected Component headerSubtitle() { return Component.literal("Confira os limites • proposta válida por 60 segundos"); }
    @Override protected void initPanel() {
        addRenderableWidget(panelButton(panelX + 16, footerTop() + 8, 100, 20, Component.literal("Recusar"), this::onClose));
        var pay = addRenderableWidget(panelButton(panelRight() - 144, footerTop() + 8, 128, 20,
                Component.literal("Pagar e adquirir"), () -> answer(true)));
        pay.active = data.balance() >= data.price();
    }
    @Override protected void renderPanelContent(GuiGraphics g, int mx, int my, float delta) {
        for (int i = 0; i < lines.size(); i++)
            g.drawString(font, font.plainSubstrByWidth(lines.get(i), panelWidth - 32), panelX + 16,
                    bodyTop() + 8 + i * 15, i == 5 ? theme.text().accent() : theme.text().body(), false);
    }
    private void answer(boolean accepted) {
        if (answered) return;
        answered = true;
        PacketDistributor.sendToServer(new LandPayloads.Respond(data.token(), accepted));
        minecraft.setScreen(null);
    }
    @Override public void onClose() { answer(false); }
    @Override public void removed() {
        if (!answered) { answered = true; PacketDistributor.sendToServer(new LandPayloads.Respond(data.token(), false)); }
        super.removed();
    }
    @Override public boolean isPauseScreen() { return false; }
}
