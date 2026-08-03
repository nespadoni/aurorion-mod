package com.aurorion.talk.client;

import com.aurorion.talk.style.BalloonStyle;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * Cache client-side de {@code UUID -> estilo}, alimentado pelos pacotes do servidor.
 */
public final class ClientStyles {
    private static final Map<UUID, BalloonStyle> STYLES = new HashMap<>();

    private ClientStyles() {
    }

    public static void replaceAll(Map<UUID, BalloonStyle> incoming) {
        STYLES.clear();
        STYLES.putAll(incoming);
    }

    public static void put(UUID player, @Nullable BalloonStyle style) {
        if (style == null) STYLES.remove(player);
        else STYLES.put(player, style);
    }

    public static BalloonStyle get(UUID player) {
        return STYLES.getOrDefault(player, BalloonStyle.DEFAULT);
    }

    /** Chamado ao sair do mundo — sem isso o cache vaza entre servidores. */
    public static void clear() {
        STYLES.clear();
    }
}
