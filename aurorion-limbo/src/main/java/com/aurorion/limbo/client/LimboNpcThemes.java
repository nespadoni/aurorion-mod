package com.aurorion.limbo.client;

import com.aurorion.core.client.gui.NpcScreenTheme;

/** Temas de NPC do Limbo. As mesmas cores sao repetidas no JSON do ADM. */
public final class LimboNpcThemes {
    public static final NpcScreenTheme ORACLE = new NpcScreenTheme(
            ClientLimbo.FONT,
            new NpcScreenTheme.PanelColors(
                    0x7804090F, 0xC0080D16,
                    0x85000000, 0xFF4A5468,
                    0xF60E131B, 0xF6111924,
                    0xFF2B3444,
                    0x42263243, 0x28263243, 0x70324760,
                    0x66000000, 0xFF65758C),
            new NpcScreenTheme.TextColors(
                    0xFFE8DCC8, 0xFFF1E8D8, 0xFF7D8798,
                    0xFF9BBFD6, 0xFFD99891),
            new NpcScreenTheme.ButtonColors(
                    0xFF182230, 0xFF263A50, 0xFF11161E,
                    0xFF35445A, 0xFF82B0CD,
                    0xFFF1E8D8, 0xFF687282)
    );

    private LimboNpcThemes() { }
}
