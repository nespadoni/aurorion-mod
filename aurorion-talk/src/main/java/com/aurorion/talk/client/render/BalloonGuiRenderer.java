package com.aurorion.talk.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.List;
import java.util.Optional;

/**
 * Desenha um balao "achatado" na tela, para os previews da GUI de personalizacao. Usa a mesma
 * geometria de {@link BalloonNineSlice} que o balao 3D acima da cabeca do jogador, entao o preview
 * nunca fica diferente do resultado real no mundo.
 */
public final class BalloonGuiRenderer {
    private static final int LINE_HEIGHT = 9;
    private static final int DECORATION_SIZE = 16;

    private BalloonGuiRenderer() {
    }

    /**
     * @param anchorX centro horizontal da ponta da seta (o "bico" do balao)
     * @param anchorY topo do texto, contando a partir da ponta da seta para cima
     */
    public static void draw(GuiGraphics graphics, Font font, ResourceLocation skin, Optional<ResourceLocation> decoration,
                            int color, int textColor, List<FormattedCharSequence> lines, int minWidth, int maxWidth,
                            int anchorX, int anchorY) {
        int widest = 0;
        for (FormattedCharSequence line : lines) widest = Math.max(widest, font.width(line));

        int width = Mth.clamp(widest, minWidth, maxWidth);
        if (width % 2 == 0) width--;

        int lineCount = lines.size();

        setTint(color);
        BalloonNineSlice.emit(
                (x, y, w, h, u, v, uw, vh) -> graphics.blit(skin, anchorX + x, anchorY + y, w, h, u, v, uw, vh, 32, 32),
                width, lineCount, 0, true
        );
        clearTint();

        decoration.ifPresent(deco -> {
            int top = BalloonNineSlice.top(lineCount, 0);
            int x = anchorX - DECORATION_SIZE / 2;
            int y = anchorY + top - DECORATION_SIZE + 4;
            graphics.blit(deco, x, y, DECORATION_SIZE, DECORATION_SIZE, 0, 0, DECORATION_SIZE, DECORATION_SIZE, DECORATION_SIZE, DECORATION_SIZE);
        });

        int y = anchorY - (LINE_HEIGHT * lineCount - 10);
        for (FormattedCharSequence line : lines) {
            graphics.drawString(font, line, anchorX - font.width(line) / 2 + 1, y, textColor, false);
            y += LINE_HEIGHT;
        }
    }

    /** Quebra o texto do jeito que o balao real quebraria — usado tanto no preview quanto no jogo. */
    public static List<FormattedCharSequence> wrap(Font font, String text, int maxWidth) {
        return font.split(FormattedText.of(text), maxWidth);
    }

    /** Altura total (em pixels) que um balao com {@code lineCount} linhas ocupa, seta inclusa. */
    public static int height(int lineCount) {
        return -BalloonNineSlice.top(lineCount, 0) + 9;
    }

    private static void setTint(int rgb) {
        float r = ((rgb >> 16) & 0xFF) / 255.0F;
        float g = ((rgb >> 8) & 0xFF) / 255.0F;
        float b = (rgb & 0xFF) / 255.0F;
        RenderSystem.setShaderColor(r, g, b, 1.0F);
    }

    private static void clearTint() {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }
}
