package com.aurorion.economia.client.gui;

import com.aurorion.core.client.gui.NpcPanelScreen;
import com.aurorion.economia.config.EconomyConfig;
import com.aurorion.economia.land.LandDeed;
import com.aurorion.economia.money.Money;
import com.aurorion.economia.network.LandPayloads;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public final class LandComposerScreen extends NpcPanelScreen {
    private final LandPayloads.Open data;
    private EditBox x, z, lotWidth, lotLength, amount;
    private Button zoneButton, send;
    private int zone;
    private String minimum = "";
    private String bounds = "";

    public LandComposerScreen(LandPayloads.Open data) {
        super(Component.literal("Venda de terreno"), Component.literal("CORRETOR • " + data.buyer()), EconomyTheme.THEME);
        this.data = data;
    }
    @Override protected PanelLayout resolveLayout() { return new PanelLayout(400, 58, 152, 34); }
    @Override protected Component headerSubtitle() { return Component.literal("Primeiro canto perto de você • retângulo +X / +Z"); }
    @Override protected void initPanel() {
        String oldX = x == null ? Integer.toString(data.origin().getX()) : x.getValue();
        String oldZ = z == null ? Integer.toString(data.origin().getZ()) : z.getValue();
        String oldW = lotWidth == null ? "20" : lotWidth.getValue();
        String oldL = lotLength == null ? "20" : lotLength.getValue();
        String oldAmount = amount == null ? "" : amount.getValue();
        int column = (panelWidth - 40) / 2;
        x = field(panelX + 16, bodyTop() + 14, column, "X do canto", oldX);
        z = field(panelX + 24 + column, bodyTop() + 14, column, "Z do canto", oldZ);
        lotWidth = field(panelX + 16, bodyTop() + 48, column, "Largura", oldW);
        lotLength = field(panelX + 24 + column, bodyTop() + 48, column, "Comprimento", oldL);
        zoneButton = addRenderableWidget(panelButton(panelX + 16, bodyTop() + 74, panelWidth - 32, 20,
                Component.literal(EconomyConfig.ZONE_NAMES[zone]), () -> {
                    zone = (zone + 1) % 5;
                    zoneButton.setMessage(Component.literal(EconomyConfig.ZONE_NAMES[zone]));
                    useMinimum();
                }));
        amount = field(panelX + 16, bodyTop() + 112, column, "Preço final em Óbolos", oldAmount);
        addRenderableWidget(panelButton(panelX + 24 + column, bodyTop() + 112, column, 20,
                Component.literal("Usar preço mínimo"), this::useMinimum));
        addRenderableWidget(panelButton(panelX + 16, footerTop() + 7, 90, 20, Component.literal("Cancelar"), this::onClose));
        send = addRenderableWidget(panelButton(panelRight() - 150, footerTop() + 7, 134, 20,
                Component.literal("Enviar proposta"), () -> {
                    if (!send.active) return;
                    PacketDistributor.sendToServer(new LandPayloads.Submit(data.token(), number(x), number(z),
                            number(lotWidth), number(lotLength), zone, amount.getValue()));
                    minecraft.setScreen(null);
                }));
        x.setResponder(value -> refresh()); z.setResponder(value -> refresh());
        lotWidth.setResponder(value -> useMinimum()); lotLength.setResponder(value -> useMinimum());
        amount.setResponder(value -> refresh());
        if (oldAmount.isBlank()) useMinimum(); else refresh();
    }
    private EditBox field(int xPos, int y, int size, String label, String value) {
        EditBox field = new EditBox(font, xPos, y, size, 20, Component.literal(label));
        field.setMaxLength(24); field.setValue(value); addRenderableWidget(field); return field;
    }
    private static int number(EditBox field) { return Integer.parseInt(field.getValue()); }
    private long minimumValue() {
        return LandDeed.minimum(number(lotWidth), number(lotLength), data.rates().get(zone), data.floor());
    }
    private void useMinimum() {
        try { long cost = minimumValue(); amount.setValue((cost / 10) + "," + (cost % 10)); }
        catch (IllegalArgumentException ignored) { }
        refresh();
    }
    private void refresh() {
        send.active = false;
        minimum = "Informe medidas válidas (até " + data.maxSide() + ").";
        bounds = "";
        try {
            long min = minimumValue();
            int w = number(lotWidth), l = number(lotLength), startX = number(x), startZ = number(z);
            minimum = "Mínimo: " + Money.format(min) + " • " + ((long) w * l) + " m²";
            bounds = "Canto final: " + ((long) startX + w - 1) + ", " + ((long) startZ + l - 1);
            send.active = w <= data.maxSide() && l <= data.maxSide() && Money.parse(amount.getValue()) >= min;
        } catch (IllegalArgumentException ignored) { }
    }
    @Override protected void renderPanelContent(GuiGraphics g, int mx, int my, float delta) {
        int col = (panelWidth - 40) / 2;
        label(g, "X do canto", 16, 3); label(g, "Z do canto", 24 + col, 3);
        label(g, "Largura (+X)", 16, 37); label(g, "Comprimento (+Z)", 24 + col, 37);
        label(g, minimum, 16, 99); label(g, bounds, 16, 139);
    }
    private void label(GuiGraphics g, String text, int xOffset, int yOffset) {
        g.drawString(font, font.plainSubstrByWidth(text, panelWidth - 32), panelX + xOffset,
                bodyTop() + yOffset, theme.text().muted(), false);
    }
    @Override public boolean isPauseScreen() { return false; }
}
