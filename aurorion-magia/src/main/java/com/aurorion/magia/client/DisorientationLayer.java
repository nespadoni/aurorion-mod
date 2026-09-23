package com.aurorion.magia.client;

import com.aurorion.magia.AurorionMagia;
import com.aurorion.magia.registry.MagiaEffects;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;

/**
 * Escurecimento parcial da tela sob Imperium Mentis: um veu verde-escuro que pulsa devagar, com as
 * bordas de cima e de baixo mais fechadas. Parcial de proposito — o jogador ainda precisa enxergar
 * para fugir.
 *
 * <p>Tres retangulos por frame e so enquanto o efeito existir; sem textura, sem alocacao.
 */
public final class DisorientationLayer implements LayeredDraw.Layer {
    public static final ResourceLocation ID = AurorionMagia.id("desorientacao");

    private static final int TINT = 0x06140F;
    private static final int FADE_TICKS = 20;

    @Override
    public void render(GuiGraphics graphics, DeltaTracker delta) {
        Player player = Minecraft.getInstance().player;
        if (player == null) return;
        MobEffectInstance effect = player.getEffect(MagiaEffects.DISORIENTED);
        if (effect == null) return;

        // Sai em fade no ultimo segundo, em vez de sumir de uma vez.
        float fade = effect.isInfiniteDuration() ? 1f : Mth.clamp(effect.getDuration() / (float) FADE_TICKS, 0f, 1f);
        float time = player.tickCount + delta.getGameTimeDeltaPartialTick(false);
        float pulse = 0.85f + 0.15f * Mth.sin(time * 0.15f);

        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        int veil = (int) (0x70 * fade * pulse) << 24 | TINT;
        int edge = (int) (0x90 * fade) << 24 | TINT;
        int band = height / 4;

        graphics.fill(0, 0, width, height, veil);
        graphics.fillGradient(0, 0, width, band, edge, TINT);
        graphics.fillGradient(0, height - band, width, height, TINT, edge);
    }
}
