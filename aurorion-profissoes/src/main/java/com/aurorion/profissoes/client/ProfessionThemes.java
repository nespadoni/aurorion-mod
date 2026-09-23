package com.aurorion.profissoes.client;

import com.aurorion.core.client.gui.NpcScreenTheme;
import net.minecraft.resources.ResourceLocation;

/** Identidade visual dos oficios: a mesma no atendimento entre jogadores e nos NPCs. */
public final class ProfessionThemes {
    public static final NpcScreenTheme OFFICE = new NpcScreenTheme(
            ResourceLocation.parse("aurorion_profissoes:display"),
            new NpcScreenTheme.PanelColors(0x80070C11, 0xD0080D16, 0x85000000, 0xFF57645F,
                    0xF50E191C, 0xF5112025, 0xFF35423F, 0x50334946, 0x28334946, 0x80506961, 0x66000000, 0xFFB6A16F),
            new NpcScreenTheme.TextColors(0xFFE8DCC8, 0xFFF1E8D8, 0xFFACB9B3, 0xFFD1BA83, 0xFFE0A296),
            new NpcScreenTheme.ButtonColors(0xFF213C3D, 0xFF305653, 0xFF182728, 0xFF687B69, 0xFFD1BA83, 0xFFF1E8D8, 0xFF81918B));

    private ProfessionThemes() {}
}
