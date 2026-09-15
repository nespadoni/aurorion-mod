package com.aurorion.core.client.gui;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

/**
 * Identidade visual de uma tela narrativa Aurorion.
 *
 * <p>O Core conhece apenas cores e a localizacao da fonte. Cada mod e dono dos assets e escolhe seu
 * tema, o que permite reaproveitar a mesma interface sem criar uma dependencia entre narrativas.
 */
public record NpcScreenTheme(
        ResourceLocation displayFont,
        PanelColors panel,
        TextColors text,
        ButtonColors button
) {
    public MutableComponent display(Component component) {
        return component.copy().withStyle(style -> style.withFont(displayFont));
    }

    public record PanelColors(
            int overlayTop,
            int overlayBottom,
            int shadow,
            int border,
            int surfaceTop,
            int surfaceBottom,
            int divider,
            int row,
            int alternateRow,
            int hoveredRow,
            int scrollTrack,
            int scrollThumb
    ) { }

    public record TextColors(
            int title,
            int body,
            int muted,
            int accent,
            int warning
    ) { }

    public record ButtonColors(
            int surface,
            int hoveredSurface,
            int disabledSurface,
            int border,
            int hoveredBorder,
            int label,
            int disabledLabel
    ) { }
}
