package com.aurorion.utils.client;

import com.aurorion.utils.AurorionUtils;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Geada nas bordas da tela de quem esta congelado: o jogador entende na hora por que as teclas
 * pararam. Quatro faixas em degrade, respirando devagar; nada quando nao esta congelado.
 */
public final class FrostLayer implements LayeredDraw.Layer {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(AurorionUtils.MOD_ID, "geada");
    private static final int FROST = 0xCFEFFF;

    @Override
    public void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !FreezeClientEvents.frozen()) return;

        float time = minecraft.player.tickCount + delta.getGameTimeDeltaPartialTick(false);
        int alpha = (int) (0x55 + 0x18 * Mth.sin(time * 0.06f));
        int edge = alpha << 24 | FROST;
        int clear = FROST;
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        int bandY = height / 6;
        int bandX = width / 8;

        graphics.fillGradient(0, 0, width, bandY, edge, clear);
        graphics.fillGradient(0, height - bandY, width, height, clear, edge);
        // fillGradient so faz degrade vertical; as laterais sao faixas finas empilhadas.
        for (int i = 0; i < 6; i++) {
            int stripe = (int) (alpha * (6 - i) / 6f) << 24 | FROST;
            int x = bandX * i / 6;
            int w = Math.max(1, bandX / 6);
            graphics.fill(x, 0, x + w, height, stripe);
            graphics.fill(width - x - w, 0, width - x, height, stripe);
        }
    }
}
