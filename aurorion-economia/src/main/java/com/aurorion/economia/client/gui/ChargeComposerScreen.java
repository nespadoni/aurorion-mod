package com.aurorion.economia.client.gui;

import com.aurorion.core.client.gui.NpcPanelScreen;
import com.aurorion.economia.money.Money;
import com.aurorion.economia.network.EconomyPayloads;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** Quem cobra informa somente o valor; profissao nao participa desta tela. */
public final class ChargeComposerScreen extends NpcPanelScreen {
    private final EconomyPayloads.OpenComposer data;
    private EditBox amount;
    private Button send;

    public ChargeComposerScreen(EconomyPayloads.OpenComposer data) {
        super(Component.literal("Cobrar " + data.targetName()), Component.literal("COBRANÇA DIRETA"), EconomyTheme.THEME);
        this.data = data;
    }

    @Override protected PanelLayout resolveLayout() { return new PanelLayout(360, 62, 102, 38); }
    @Override protected Component headerSubtitle() { return Component.literal("1 Óbolo = 10 Fragmentos"); }

    @Override
    protected void initPanel() {
        amount = new EditBox(font, panelX + 32, bodyTop() + 39, panelWidth - 64, 22,
                Component.literal("Valor em Óbolos"));
        amount.setMaxLength(32);
        amount.setFilter(ChargeComposerScreen::looksLikeMoney);
        amount.setHint(Component.literal("Ex.: 4,5").withStyle(ChatFormatting.DARK_GRAY));
        amount.setResponder(value -> send.active = valid(value));
        addRenderableWidget(amount);
        setInitialFocus(amount);

        addRenderableWidget(panelButton(panelX + 16, footerTop() + 8, 96, 22,
                Component.literal("Cancelar"), this::onClose));
        send = panelButton(panelRight() - 128, footerTop() + 8, 112, 22,
                Component.literal("Enviar cobrança"), this::submit);
        send.active = false;
        addRenderableWidget(send);
    }

    @Override
    protected void renderPanelContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.drawString(font, Component.literal("Quanto " + data.targetName() + " deve pagar?"),
                panelX + 32, bodyTop() + 15, theme.text().body(), false);
        graphics.drawString(font, Component.literal("A transferência só acontece depois do aceite."),
                panelX + 32, bodyTop() + 72, theme.text().muted(), false);
    }

    private void submit() {
        if (!send.active) return;
        send.active = false;
        PacketDistributor.sendToServer(new EconomyPayloads.Submit(data.target(), amount.getValue()));
        minecraft.setScreen(null);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER)
                && send != null && send.active) {
            submit();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private static boolean valid(String value) {
        try {
            Money.parse(value);
            return true;
        } catch (Money.MoneyFormatException ignored) {
            return false;
        }
    }

    private static boolean looksLikeMoney(String value) {
        if (value.isEmpty()) return true;
        int separator = -1;
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (Character.isDigit(character)) continue;
            if ((character == ',' || character == '.') && separator < 0) {
                separator = i;
                continue;
            }
            return false;
        }
        return separator < 0 || value.length() - separator <= 2;
    }

    @Override public boolean isPauseScreen() { return false; }
}
