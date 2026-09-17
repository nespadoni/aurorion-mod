package com.aurorion.economia.client.gui;

import com.aurorion.core.client.gui.NpcScreenTheme;
import net.minecraft.resources.ResourceLocation;

final class EconomyTheme {
    static final NpcScreenTheme THEME = new NpcScreenTheme(
            ResourceLocation.fromNamespaceAndPath("minecraft", "default"),
            new NpcScreenTheme.PanelColors(
                    0x90080503, 0xE0100804, 0x95000000, 0xFF6C4D24,
                    0xF51A1008, 0xF525170B, 0xFF5B3F1E, 0x553A2813,
                    0x44302110, 0x80644722, 0x66140C06, 0xFFB58A45),
            new NpcScreenTheme.TextColors(
                    0xFFF1D59B, 0xFFF0E3C9, 0xFFB8A88B, 0xFFE2B867, 0xFFE07A66),
            new NpcScreenTheme.ButtonColors(
                    0xFF493018, 0xFF684724, 0xFF241A10, 0xFF8D6634,
                    0xFFE2B867, 0xFFF6E9D0, 0xFF887967));

    private EconomyTheme() { }
}
