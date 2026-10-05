package com.aurorion.essentials.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Menu de opcoes que sobe do rodape do celular, por cima da tela atual (que continua desenhada ao
 * fundo). Usado no "+" do Gram para escolher entre camera e galeria. A ultima linha e "Cancelar".
 */
public class PhoneChoiceSheet extends Screen {
    private static final int ROW = 22;
    private static final int MARGIN = 12;

    public record Option(String label, Runnable action) {
    }

    private final Screen parent;
    private final List<Option> options;

    public PhoneChoiceSheet(Screen parent, String title, List<Option> options) {
        super(Component.literal(title));
        this.parent = parent;
        this.options = List.copyOf(options);
    }

    @Override
    public void resize(Minecraft minecraft, int width, int height) {
        if (parent != null) parent.resize(minecraft, width, height);
        super.resize(minecraft, width, height);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (parent != null) parent.render(graphics, -1, -1, partialTick);
        int x = PhoneUi.phoneLeft(this);
        int y = PhoneUi.phoneTop(this);
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 400);
        graphics.fill(x + 6, y + 6, x + PhoneUi.PHONE_WIDTH - 6, y + PhoneUi.PHONE_HEIGHT - 6, 0x88000000);

        int left = x + MARGIN;
        int right = x + PhoneUi.PHONE_WIDTH - MARGIN;
        int sheetTop = sheetTop();
        graphics.fill(left, sheetTop, right, y + PhoneUi.PHONE_HEIGHT - 14, PhoneUi.themeColor("getCardColor", 0xFFFFFFFF));
        graphics.drawString(font, title, left + 8, sheetTop + 7, PhoneUi.themeColor("getSubTextColor", 0xFF6B7280), false);

        for (int i = 0; i <= options.size(); i++) {
            int rowTop = rowTop(i);
            boolean hover = onRow(mouseX, mouseY, i);
            graphics.fill(left + 4, rowTop, right - 4, rowTop + ROW - 3, hover
                    ? PhoneUi.themeColor("getReadableInputHoverColor", 0xFFE5E7EB)
                    : PhoneUi.themeColor("getSecondaryCardColor", 0xFFF3F4F6));
            boolean cancel = i == options.size();
            graphics.drawCenteredString(font, cancel ? "Cancelar" : options.get(i).label(), (left + right) / 2, rowTop + 6,
                    cancel ? PhoneUi.themeColor("getTextColor", 0xFF111827) : PhoneUi.themeColor("getAccentColor", 0xFF1F6FEB));
        }
        graphics.pose().popPose();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // A tela do celular por baixo ja e o fundo.
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (int i = 0; i < options.size(); i++) {
            if (onRow(mouseX, mouseY, i)) {
                PhoneUi.playClick();
                options.get(i).action().run();
                return true;
            }
        }
        // Cancelar, ou toque fora do menu.
        PhoneUi.playBack();
        onClose();
        return true;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int sheetTop() {
        return PhoneUi.phoneTop(this) + PhoneUi.PHONE_HEIGHT - 14 - (24 + (options.size() + 1) * ROW);
    }

    private int rowTop(int index) {
        return sheetTop() + 20 + index * ROW;
    }

    private boolean onRow(double mouseX, double mouseY, int index) {
        int left = PhoneUi.phoneLeft(this) + MARGIN + 4;
        return PhoneIcons.inside(mouseX, mouseY, left, rowTop(index), PhoneUi.PHONE_WIDTH - 2 * MARGIN - 8, ROW - 3);
    }
}
