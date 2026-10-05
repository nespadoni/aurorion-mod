package com.aurorion.essentials.client;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Icones 11x11 desenhados pixel a pixel, no mesmo estilo chapado do telefone (que desenha tudo com
 * retangulos). Sem textura: fica nitido em qualquer escala de GUI e na cor do tema.
 */
public final class PhoneIcons {
    public static final int SIZE = 11;

    public static final String[] CAMERA = {
            "...........",
            "...###.....",
            ".#########.",
            ".#.......#.",
            ".#..###..#.",
            ".#.#...#.#.",
            ".#.#...#.#.",
            ".#..###..#.",
            ".#.......#.",
            ".#########.",
            "..........."
    };
    public static final String[] LOCATION = {
            "....###....",
            "...#####...",
            "..##...##..",
            "..##...##..",
            "..##...##..",
            "...#####...",
            "...#####...",
            "....###....",
            "....###....",
            ".....#.....",
            "..........."
    };
    public static final String[] CLIP = {
            ".....###...",
            "....#...#..",
            "...#..#..#.",
            "...#.#.#.#.",
            "..#..#.#.#.",
            "..#.#..#.#.",
            "..#.#.#..#.",
            "..#.#.#.#..",
            "..#..#..#..",
            "...#...#...",
            "....###...."
    };
    public static final String[] IMPORT = {
            ".....#.....",
            "....###....",
            "...#.#.#...",
            ".....#.....",
            ".....#.....",
            ".....#.....",
            ".#...#...#.",
            ".#.......#.",
            ".#.......#.",
            ".#########.",
            "..........."
    };

    private PhoneIcons() {
    }

    public static void draw(GuiGraphics graphics, String[] icon, int x, int y, int color) {
        for (int row = 0; row < icon.length; row++) {
            String line = icon[row];
            int start = -1;
            for (int col = 0; col <= line.length(); col++) {
                boolean on = col < line.length() && line.charAt(col) == '#';
                if (on && start < 0) start = col;
                if (!on && start >= 0) {
                    graphics.fill(x + start, y + row, x + col, y + row + 1, color);
                    start = -1;
                }
            }
        }
    }

    /** Botao quadrado do telefone com o icone centralizado. */
    public static void button(GuiGraphics graphics, String[] icon, int x, int y, int width, int height,
                              boolean hover) {
        int background = hover
                ? PhoneUi.themeColor("getAttachmentButtonHoverColor", 0xFFD7E3F5)
                : PhoneUi.themeColor("getAttachmentButtonColor", 0xFFE7EEF8);
        graphics.fill(x, y, x + width, y + height, background);
        draw(graphics, icon, x + (width - SIZE) / 2, y + (height - SIZE) / 2,
                PhoneUi.themeColor("getAttachmentButtonTextColor", 0xFF1F6FEB));
    }

    public static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}
