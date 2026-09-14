package com.aurorion.limbo.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.List;

/** Formas em batch; quebra de texto/medidas sao refeitas somente ao mudar mensagem ou viewport. */
public final class LimboHudLayer {
    private static int lastWidth = -1, lastRevision = -1;
    private static String language = "";
    private static List<FormattedCharSequence> lines = List.of();
    private static int titleWidth, eyebrowWidth;
    private static int clockWidth;
    private static String lastClock = "";

    private LimboHudLayer() { }

    public static void render(GuiGraphics g) {
        var mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        int width = g.guiWidth(), height = g.guiHeight();
        if (ClientLimbo.active()) status(g, width);
        var notice = ClientLimbo.notice();
        if (notice == null) return;
        boolean major = ClientLimbo.major();
        int boxWidth = Math.min(major ? 360 : 300, width - 24);
        if (boxWidth < 80) return;
        if (lastWidth != boxWidth || lastRevision != ClientLimbo.revision() || !language.equals(mc.options.languageCode)) {
            lastWidth = boxWidth; lastRevision = ClientLimbo.revision(); language = mc.options.languageCode;
            lines = mc.font.split(notice.body(), boxWidth - 38);
            titleWidth = mc.font.width(ClientLimbo.noticeTitle());
            eyebrowWidth = mc.font.width(ClientLimbo.EYEBROW);
        }
        float age = ClientLimbo.ageSeconds();
        float alpha = Math.min(Mth.clamp(age / .65F, 0, 1), Mth.clamp((ClientLimbo.durationSeconds() - age) / 1.0F, 0, 1));
        if (alpha < .025F) return;
        int accent = color(ClientLimbo.accent(), alpha);
        int boxHeight = (major ? 83 : 54) + lines.size() * 12;
        int x = (width - boxWidth) / 2;
        int baseY = major ? (int) (height * .33F) : height - boxHeight - 50;
        int y = Math.max(8, Math.min(height - boxHeight - 8, baseY)) + (int) ((1 - alpha) * 6);
        g.fillGradient(x, y, x + boxWidth, y + boxHeight, color(0x101925, alpha * .93F), color(0x080C14, alpha * .88F));
        g.fill(x, y, x + boxWidth, y + 1, color(0x75899E, alpha * .4F));
        g.fill(x, y + boxHeight - 1, x + boxWidth, y + boxHeight, color(0x75899E, alpha * .25F));
        corners(g, x, y, boxWidth, boxHeight, accent);
        int mid = width / 2;
        float scale = Math.min(major ? 1.7F : 1.1F, (boxWidth - 36F) / Math.max(1, titleWidth));
        if (major) {
            g.drawString(mc.font, ClientLimbo.EYEBROW, mid - eyebrowWidth / 2, y + 13, color(0x8B9CAD, alpha), false);
            rule(g, mid, y + 29, accent, alpha);
        }
        int titleY = y + (major ? 40 : 13);
        g.pose().pushPose();
        g.pose().translate(mid - titleWidth * scale / 2, titleY, 0);
        g.pose().scale(scale, scale, 1);
        g.drawString(mc.font, ClientLimbo.noticeTitle(), 0, 0, color(0xF1E8D8, alpha), false);
        g.pose().popPose();
        int textY = titleY + (major ? 29 : 20);
        for (int i = 0; i < lines.size(); i++) {
            var line = lines.get(i);
            g.drawString(mc.font, line, mid - mc.font.width(line) / 2, textY + i * 12, color(0xBEC9D5, alpha), false);
        }
        int lineY = y + boxHeight - 10;
        int progressWidth = (int) ((boxWidth - 36) * Math.max(0, 1 - age / ClientLimbo.durationSeconds()));
        g.fill(x + 18, lineY, x + 18 + progressWidth, lineY + 1, color(ClientLimbo.accent(), alpha * .5F));
    }

    private static void status(GuiGraphics g, int screenWidth) {
        var font = Minecraft.getInstance().font;
        int width = Math.min(158, screenWidth - 24), x = screenWidth - width - 12, y = 12;
        g.fillGradient(x, y, x + width, y + 70, 0xE6101824, 0xD6090E17);
        g.fill(x, y, x + 2, y + 70, 0xFF91B7CB);
        g.fill(x + 12, y + 26, x + width - 12, y + 27, 0x465D7D93);
        g.drawString(font, ClientLimbo.NAME, x + 12, y + 9, 0xFFF0E5D3, false);
        g.drawString(font, ClientLimbo.REMAINING, x + 12, y + 34, 0xFF8295A8, false);
        String clock = ClientLimbo.clock();
        if (!lastClock.equals(clock)) { lastClock = clock; clockWidth = font.width(clock); }
        g.drawString(font, clock, x + width - 12 - clockWidth, y + 34, 0xFFDEE9F1, false);
        g.drawString(font, ClientLimbo.stage(), x + 12, y + 52, 0xFFA7BED0, false);
    }

    private static void corners(GuiGraphics g, int x, int y, int w, int h, int color) {
        int length = 8;
        g.fill(x, y, x + length, y + 1, color); g.fill(x, y, x + 1, y + length, color);
        g.fill(x + w - length, y, x + w, y + 1, color); g.fill(x + w - 1, y, x + w, y + length, color);
        g.fill(x, y + h - 1, x + length, y + h, color); g.fill(x, y + h - length, x + 1, y + h, color);
        g.fill(x + w - length, y + h - 1, x + w, y + h, color); g.fill(x + w - 1, y + h - length, x + w, y + h, color);
    }
    private static void rule(GuiGraphics g, int mid, int y, int accent, float alpha) {
        g.fill(mid - 35, y, mid - 8, y + 1, color(0x6B8297, alpha * .5F));
        g.fill(mid + 8, y, mid + 35, y + 1, color(0x6B8297, alpha * .5F));
        g.fill(mid - 1, y - 2, mid + 2, y + 3, accent);
        g.fill(mid - 2, y - 1, mid + 3, y + 2, accent);
    }
    private static int color(int rgb, float alpha) { return (Math.clamp((int) (alpha * 255), 0, 255) << 24) | rgb; }
}
