package com.aurorion.essentials.fakename;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/**
 * Traduz codigos de cor estilo Bukkit ("&6", "&l"...) para um {@link Component} com
 * {@link Style} de verdade por trecho, em vez de deixar o caractere de secao cru dentro do
 * texto — um literal com "§6" embutido nao e recolorido no render, o Style e que manda.
 */
public final class LegacyColorCodes {
    private LegacyColorCodes() {
    }

    public static Component parse(String raw) {
        MutableComponent result = Component.empty();
        StringBuilder segment = new StringBuilder();
        Style style = Style.EMPTY;

        int i = 0;
        int length = raw.length();
        while (i < length) {
            char c = raw.charAt(i);

            if (c == '&' && i + 1 < length) {
                ChatFormatting formatting = ChatFormatting.getByCode(Character.toLowerCase(raw.charAt(i + 1)));
                if (formatting != null) {
                    if (!segment.isEmpty()) {
                        result.append(Component.literal(segment.toString()).setStyle(style));
                        segment.setLength(0);
                    }
                    // RESET (&r) zera cor e todos os formatos acumulados, nao so o proprio flag.
                    style = formatting == ChatFormatting.RESET ? Style.EMPTY : style.applyFormat(formatting);
                    i += 2;
                    continue;
                }
            }

            segment.append(c);
            i++;
        }

        if (!segment.isEmpty()) {
            result.append(Component.literal(segment.toString()).setStyle(style));
        }

        return result;
    }
}
