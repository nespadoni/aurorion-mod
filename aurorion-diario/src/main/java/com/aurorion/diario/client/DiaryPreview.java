package com.aurorion.diario.client;

import com.aurorion.diario.markup.DiaryMarkup;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * A prévia do diário: o mesmo documento que o site renderiza, desenhado com o texto do Minecraft.
 * Título dourado, citação em itálico com a barra, lista com marcador, separador e imagem como
 * aviso (imagens aparecem no site).
 */
final class DiaryPreview {
    private static final int INK = 0xFFEDE0CB;
    private static final int GOLD = 0xFFFFE9A7;
    private static final int QUOTE = 0xFFBFB3D6;
    private static final int FAINT = 0xFF8C8098;
    private static final int LINE = 9;

    private record Line(FormattedCharSequence text, int indent, int color, boolean bar, boolean divider, int gapAfter) {
    }

    private final List<Line> lines = new ArrayList<>();
    private int scroll;

    DiaryPreview(Font font, String markup, int width) {
        JsonObject document = DiaryMarkup.toDocument(markup);
        if (!(document.get("blocks") instanceof JsonArray blocks)) return;
        for (JsonElement element : blocks) {
            if (!(element instanceof JsonObject block)) continue;
            String type = block.get("type").getAsString();
            switch (type) {
                case "heading" -> add(font, runs(block.get("runs"), Style.EMPTY.withBold(true)), width, 0, GOLD, false, 4);
                case "quote" -> add(font, runs(block.get("runs"), Style.EMPTY.withItalic(true)), width - 10, 10, QUOTE, true, 4);
                case "list" -> {
                    if (block.get("items") instanceof JsonArray items) {
                        for (JsonElement item : items) {
                            MutableComponent bullet = Component.literal("✦ ").withStyle(Style.EMPTY.withColor(GOLD & 0xFFFFFF));
                            add(font, bullet.append(runs(item, Style.EMPTY)), width - 6, 6, INK, false, 1);
                        }
                        gap(3);
                    }
                }
                case "divider" -> lines.add(new Line(FormattedCharSequence.EMPTY, 0, GOLD, false, true, 6));
                case "image" -> {
                    String caption = block.has("caption") ? block.get("caption").getAsString() : "";
                    add(font, Component.literal("[imagem" + (caption.isEmpty() ? "" : ": " + caption) + " — aparece no site]")
                            .withStyle(Style.EMPTY.withItalic(true)), width, 0, FAINT, false, 4);
                }
                default -> add(font, runs(block.get("runs"), Style.EMPTY), width, 0, INK, false, 4);
            }
        }
    }

    private void add(Font font, Component text, int width, int indent, int color, boolean bar, int gapAfter) {
        List<FormattedCharSequence> split = font.split(text, Math.max(40, width));
        for (int i = 0; i < split.size(); i++) {
            lines.add(new Line(split.get(i), indent, color, bar, false, i == split.size() - 1 ? gapAfter : 0));
        }
    }

    private void gap(int pixels) {
        if (lines.isEmpty()) return;
        Line last = lines.remove(lines.size() - 1);
        lines.add(new Line(last.text(), last.indent(), last.color(), last.bar(), last.divider(), last.gapAfter() + pixels));
    }

    private static MutableComponent runs(JsonElement runs, Style base) {
        MutableComponent out = Component.empty();
        if (!(runs instanceof JsonArray array)) return out;
        for (JsonElement element : array) {
            if (!(element instanceof JsonObject run)) continue;
            boolean bold = false;
            boolean italic = false;
            if (run.get("marks") instanceof JsonArray marks) {
                for (JsonElement mark : marks) {
                    bold |= "bold".equals(mark.getAsString());
                    italic |= "italic".equals(mark.getAsString());
                }
            }
            Style style = base;
            if (bold) style = style.withBold(true);
            if (italic) style = style.withItalic(true);
            out.append(Component.literal(run.get("text").getAsString()).withStyle(style));
        }
        return out;
    }

    int contentHeight() {
        int height = 0;
        for (Line line : lines) height += (line.divider() ? 1 : LINE) + line.gapAfter();
        return height;
    }

    void scroll(double amount, int viewHeight) {
        scroll = (int) Math.max(0, Math.min(Math.max(0, contentHeight() - viewHeight), scroll - amount * LINE * 2));
    }

    void render(GuiGraphics graphics, Font font, int x, int y, int width, int height) {
        graphics.enableScissor(x, y, x + width, y + height);
        int cy = y - scroll;
        for (Line line : lines) {
            if (line.divider()) {
                int mid = x + width / 2;
                graphics.fill(mid - width / 4, cy + 2, mid + width / 4, cy + 3, 0x99D7A857);
                cy += 1 + line.gapAfter();
                continue;
            }
            if (cy + LINE >= y && cy <= y + height) {
                if (line.bar()) graphics.fill(x + 2, cy - 1, x + 4, cy + LINE, 0xAAD7A857);
                graphics.drawString(font, line.text(), x + line.indent(), cy, line.color(), false);
            }
            cy += LINE + line.gapAfter();
        }
        graphics.disableScissor();
    }
}
