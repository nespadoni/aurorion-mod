package com.aurorion.essentials.client;

import com.aurorion.essentials.fakename.FakeName;
import com.aurorion.essentials.fakename.FakeNameRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Nomes visuais apenas: o telefone usa os nicks originais para encaminhar chamadas e mensagens. */
public final class MattupolisPhoneNames {
    private static final Map<String, UUID> KNOWN_PLAYERS = new HashMap<>();
    private static ClientPacketListener connection;

    private MattupolisPhoneNames() {}

    public static String display(String text) {
        var minecraft = Minecraft.getInstance();
        var current = minecraft.getConnection();
        if (connection != current) {
            KNOWN_PLAYERS.clear();
            connection = current;
        }
        if (current == null || text == null || text.isBlank()) return text;

        UUID account = null;
        for (var info : current.getOnlinePlayers()) {
            if (info.getProfile().getName().equalsIgnoreCase(text)) {
                account = info.getProfile().getId();
                KNOWN_PLAYERS.put(text.toLowerCase(Locale.ROOT), account);
                break;
            }
        }
        // Mantem a apresentacao de um contato que desconectou durante esta sessao.
        if (account == null) account = KNOWN_PLAYERS.get(text.toLowerCase(Locale.ROOT));
        FakeName fake = account == null ? null : FakeNameRegistry.get(account);
        return fake == null ? text : fake.plain();
    }

    public static String callLabel(String name) {
        String display = display(name);
        var font = Minecraft.getInstance().font;
        return font.width(display) <= 144 ? display : font.plainSubstrByWidth(display, 132) + "...";
    }
}
