package com.aurorion.limbo.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/** Credits-inspired original epilogue. Wrapping happens on init/resize, never per frame. */
public final class FinaleScreen extends Screen {
    private static final Component DEAD = Component.translatable("aurorion_limbo.finale.dead");
    private static final Component PREVIEW = Component.translatable("aurorion_limbo.finale.preview");
    private final List<FormattedCharSequence> credits = new ArrayList<>();
    private List<FormattedCharSequence> phrase = List.of();
    public FinaleScreen() { super(DEAD); }
    @Override protected void init() { rebuildText(); }

    public void rebuildText() {
        if (!ClientFinale.active() || font == null) return;
        int textWidth = Math.max(100, Math.min(420, width - 48));
        credits.clear();
        for (String paragraph : ClientFinale.script().paragraphs()) {
            credits.addAll(font.split(Component.literal(paragraph), textWidth));
            credits.add(FormattedCharSequence.EMPTY);
            credits.add(FormattedCharSequence.EMPTY);
        }
        phrase = font.split(Component.literal(ClientFinale.script().phrase())
                .withStyle(style -> style.withFont(ClientLimbo.FONT)), textWidth);
    }

    @Override public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) { }
    @Override public boolean isPauseScreen() { return false; }
    @Override public boolean shouldCloseOnEsc() { return ClientFinale.preview(); }
    @Override public void onClose() { if (ClientFinale.preview()) ClientFinale.clear(); }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (!ClientFinale.active()) return;
        double elapsed = ClientFinale.elapsedMillis();
        var script = ClientFinale.script();
        double t = ClientFinale.riseProgress();
        double closure = t * t * (3 - 2 * t);
        // Eyelids meet slowly at the center; the whole scene loses light at the same time.
        if (t < 1) {
            int black = (int) (255 * Math.pow(closure, 2));
            if (black > 0) g.fill(0, 0, width, height, black << 24);
            int lid = (int) (height * .5 * closure);
            int feather = Math.max(2, height / 16);
            g.fill(0, 0, width, lid, 0xFF000000);
            g.fill(0, height - lid, width, height, 0xFF000000);
            g.fillGradient(0, lid, width, Math.min(height, lid + feather), 0xFF000000, 0);
            g.fillGradient(0, Math.max(0, height - lid - feather), width, height - lid, 0, 0xFF000000);
        } else {
            g.fill(0, 0, width, height, 0xFF000000);
            if (elapsed < script.creditsStartMillis()) {
                double age = elapsed - script.riseSeconds() * 1000L;
                double left = script.creditsStartMillis() - elapsed;
                int alpha = (int) (255 * Math.clamp(Math.min(age, left) / 2500, 0, 1));
                if (alpha >= 4) centered(g, phrase, height / 2 - phrase.size() * 8, (alpha << 24) | 0xDCD7CE, 16);
            } else if (elapsed < script.deathTitleMillis()) {
                double progress = (elapsed - script.creditsStartMillis()) / (script.creditsSeconds() * 1000.0);
                int lineHeight = 14;
                double top = height - progress * (height + credits.size() * lineHeight);
                for (int i = 0; i < credits.size(); i++) {
                    int y = (int) top + i * lineHeight;
                    if (y < -lineHeight || y > height) continue;
                    g.drawString(font, credits.get(i), (width - font.width(credits.get(i))) / 2, y, 0xFFD2CEC8, false);
                }
                int fade = Math.min(48, height / 5);
                g.fillGradient(0, 0, width, fade, 0xFF000000, 0);
                g.fillGradient(0, height - fade, width, height, 0, 0xFF000000);
            } else {
                g.drawCenteredString(font, DEAD, width / 2, height / 2 - 5, 0xFFAFA6A2);
            }
        }
        if (ClientFinale.preview()) g.drawCenteredString(font, PREVIEW, width / 2, height - 14, 0xFF888888);
    }

    private void centered(GuiGraphics g, List<FormattedCharSequence> lines, int y, int color, int spacing) {
        for (var line : lines) {
            g.drawString(font, line, (width - font.width(line)) / 2, y, color, false);
            y += spacing;
        }
    }
}
