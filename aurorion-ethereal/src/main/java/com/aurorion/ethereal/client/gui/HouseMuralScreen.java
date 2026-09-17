package com.aurorion.ethereal.client.gui;

import com.aurorion.core.text.TimeFormat;
import com.aurorion.ethereal.network.HouseMuralPayloads;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** Painel coletivo da Casa, com a área proibida visualmente separada do cofre. */
public final class HouseMuralScreen extends Screen {
    private static final int PANEL_W = 344;
    private static final int PANEL_H = 232;
    private static final int C_OVERLAY = 0xC6000000;
    private static final int C_PANEL = 0xFF0B0C10;
    private static final int C_BORDER = 0xFF4C3B56;
    private static final int C_ROW = 0xFF151820;
    private static final int C_FORBIDDEN = 0xFF050205;
    private static final int C_RED = 0xFF8D2639;
    private static final int C_TEXT = 0xFFE6E0D8;
    private static final int C_MUTED = 0xFF89848C;

    private final HouseMuralPayloads.Open data;
    private int left;
    private int top;

    public HouseMuralScreen(HouseMuralPayloads.Open data) {
        super(Component.translatable("gui.aurorion_ethereal.mural.title"));
        this.data = data;
    }

    @Override
    protected void init() {
        left = (width - PANEL_W) / 2;
        top = (height - PANEL_H) / 2;

        Component protectorLabel = data.protectorLevel() <= 0
                ? Component.translatable("gui.aurorion_ethereal.mural.protector.scrambled")
                : Component.translatable("gui.aurorion_ethereal.mural.protector.open");
        Button protector = addRenderableWidget(Button.builder(protectorLabel, button -> {
                    PacketDistributor.sendToServer(new HouseMuralPayloads.OpenProtector(data.pos()));
                }).bounds(left + 22, top + 172, PANEL_W - 44, 20).build());
        protector.active = data.protectorLevel() > 0 && data.remainingMillis() <= 0L && data.livesAvailable();

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .bounds(left + PANEL_W / 2 - 55, top + 204, 110, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(0, 0, width, height, C_OVERLAY);
        graphics.fill(left - 2, top - 2, left + PANEL_W + 2, top + PANEL_H + 2, C_BORDER);
        graphics.fill(left, top, left + PANEL_W, top + PANEL_H, C_PANEL);

        int houseColor = 0xFF000000 | data.houseColor();
        graphics.fill(left, top, left + PANEL_W, top + 3, houseColor);
        graphics.drawCenteredString(font, data.houseName(), left + PANEL_W / 2, top + 13, houseColor);
        graphics.drawCenteredString(font, title, left + PANEL_W / 2, top + 27, C_MUTED);

        graphics.fill(left + 14, top + 45, left + PANEL_W - 14, top + 112, C_ROW);
        graphics.drawString(font, Component.translatable("gui.aurorion_ethereal.mural.vault"),
                left + 24, top + 55, C_TEXT, false);
        if (data.economyAvailable()) {
            graphics.drawString(font, Component.translatable("gui.aurorion_ethereal.mural.vault.level",
                    data.vaultLevel()), left + 24, top + 74, C_MUTED, false);
            graphics.drawString(font, Component.translatable("gui.aurorion_ethereal.mural.vault.balance",
                    money(data.balance()), money(data.capacity())), left + 24, top + 91, C_TEXT, false);
        } else {
            graphics.drawString(font, Component.translatable("gui.aurorion_ethereal.mural.vault.unavailable"),
                    left + 24, top + 80, C_MUTED, false);
        }

        graphics.fill(left + 14, top + 121, left + PANEL_W - 14, top + 198, C_FORBIDDEN);
        graphics.fill(left + 14, top + 121, left + 18, top + 198, C_RED);
        Component section = data.protectorLevel() <= 0
                ? Component.translatable("gui.aurorion_ethereal.mural.protector.scrambled")
                : Component.translatable("gui.aurorion_ethereal.mural.protector.level", data.protectorLevel());
        graphics.drawCenteredString(font, section, left + PANEL_W / 2, top + 130, C_RED);
        Component state = protectorState();
        graphics.drawCenteredString(font, state, left + PANEL_W / 2, top + 147,
                data.remainingMillis() > 0L ? C_MUTED : C_TEXT);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private Component protectorState() {
        if (data.protectorLevel() <= 0) {
            return Component.translatable("gui.aurorion_ethereal.mural.protector.locked");
        }
        if (!data.livesAvailable()) {
            return Component.translatable("gui.aurorion_ethereal.mural.protector.no_lives_mod");
        }
        if (data.remainingMillis() > 0L) {
            return Component.translatable("gui.aurorion_ethereal.mural.protector.remaining",
                    TimeFormat.duration(data.remainingMillis()));
        }
        return Component.translatable("gui.aurorion_ethereal.mural.protector.ready");
    }

    private static String money(long fragments) {
        return (fragments / 10L) + "," + Math.abs(fragments % 10L) + " O";
    }

    @Override public boolean isPauseScreen() { return false; }
}
