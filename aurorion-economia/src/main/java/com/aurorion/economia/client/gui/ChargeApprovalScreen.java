package com.aurorion.economia.client.gui;

import com.aurorion.core.client.gui.NpcPanelScreen;
import com.aurorion.economia.money.Money;
import com.aurorion.economia.network.EconomyPayloads;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** Confirmacao do pagador. Fechar a tela equivale a recusar e nunca movimenta saldo. */
public final class ChargeApprovalScreen extends NpcPanelScreen {
    private final EconomyPayloads.OpenApproval data;
    private boolean answered;

    public ChargeApprovalScreen(EconomyPayloads.OpenApproval data) {
        super(Component.literal("Cobrança recebida"), Component.literal("CONFIRMAÇÃO"), EconomyTheme.THEME);
        this.data = data;
    }

    @Override protected PanelLayout resolveLayout() { return new PanelLayout(380, 62, 108, 38); }
    @Override protected Component headerSubtitle() { return Component.literal("Você decide se deseja pagar"); }

    @Override
    protected void initPanel() {
        addRenderableWidget(panelButton(panelX + 16, footerTop() + 8, 96, 22,
                Component.literal("Recusar"), () -> answer(false)));
        var pay = panelButton(panelRight() - 122, footerTop() + 8, 106, 22,
                Component.literal("Pagar"), () -> answer(true));
        pay.active = data.balance() >= data.amount();
        addRenderableWidget(pay);
    }

    @Override
    protected void renderPanelContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.drawCenteredString(font, Component.literal(data.chargerName() + " está cobrando"),
                panelX + panelWidth / 2, bodyTop() + 17, theme.text().body());
        graphics.drawCenteredString(font, Component.literal(Money.describe(data.amount())),
                panelX + panelWidth / 2, bodyTop() + 40, theme.text().accent());
        graphics.drawCenteredString(font, Component.literal("Seu saldo: " + Money.describe(data.balance())),
                panelX + panelWidth / 2, bodyTop() + 66,
                data.balance() >= data.amount() ? theme.text().muted() : theme.text().warning());
        if (data.balance() < data.amount()) graphics.drawCenteredString(font,
                Component.literal("Saldo insuficiente"), panelX + panelWidth / 2,
                bodyTop() + 84, theme.text().warning());
    }

    private void answer(boolean accepted) {
        if (answered) return;
        answered = true;
        PacketDistributor.sendToServer(new EconomyPayloads.Respond(data.token(), accepted));
        minecraft.setScreen(null);
    }

    @Override public void onClose() { answer(false); }
    @Override public boolean isPauseScreen() { return false; }
}
