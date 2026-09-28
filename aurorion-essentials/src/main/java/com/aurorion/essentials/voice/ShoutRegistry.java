package com.aurorion.essentials.voice;

import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quem esta com o {@code /gritar} ligado, e ate onde a voz dessa pessoa vai.
 *
 * <p>Fica so em memoria: sobrevive a relog, mas nao a reinicio do servidor. Grito e coisa de cena, e
 * um alcance gigante esquecido ligado por semanas seria pior do que ter de ligar de novo.
 *
 * <p>Mapa concorrente porque quem le e a thread de audio do Voice Chat (a cada pacote de microfone,
 * ~50 por segundo por pessoa falando) e quem escreve e o comando, na thread do servidor.
 */
public final class ShoutRegistry {
    private static final Map<UUID, Float> SHOUTING = new ConcurrentHashMap<>();

    private ShoutRegistry() {
    }

    public static void set(UUID account, float distance) {
        SHOUTING.put(account, distance);
    }

    /** @return true se a pessoa estava gritando. */
    public static boolean clear(UUID account) {
        return SHOUTING.remove(account) != null;
    }

    public static void clearAll() {
        SHOUTING.clear();
    }

    @Nullable
    public static Float get(UUID account) {
        return SHOUTING.get(account);
    }

    /** Copia, para listar sem segurar o mapa. */
    public static Map<UUID, Float> all() {
        return Map.copyOf(SHOUTING);
    }

    /**
     * O alcance que o Voice Chat deve usar para este pacote. Sussurro continua sussurro, e o grito so
     * aumenta: um {@code /gritar} menor que a voz normal nao encurta nada.
     */
    public static float distanceFor(UUID account, float normal, boolean whispering) {
        if (whispering) return normal;
        Float shout = SHOUTING.get(account);
        return shout == null ? normal : Math.max(normal, shout);
    }
}
