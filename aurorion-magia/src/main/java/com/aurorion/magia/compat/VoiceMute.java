package com.aurorion.magia.compat;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quem esta sem voz, e ate quando.
 *
 * <p>Separado do efeito de status porque o Simple Voice Chat consulta isto da <b>thread de audio</b>,
 * a cada pacote de microfone (dezenas por segundo por pessoa falando). Ler efeito de entidade de
 * outra thread nao e seguro; um mapa concorrente com prazo em relogio de parede e.
 *
 * <p>O prazo e o mesmo do efeito, convertido para milissegundos. Leite ou fim antecipado do efeito
 * chamam {@link #unmute}; o prazo so existe para nao depender de ninguem lembrar de limpar.
 */
public final class VoiceMute {
    private static final Map<UUID, Long> MUTED_UNTIL = new ConcurrentHashMap<>();

    private VoiceMute() {
    }

    public static void mute(UUID player, long millis) {
        MUTED_UNTIL.put(player, System.currentTimeMillis() + millis);
    }

    public static void unmute(UUID player) {
        MUTED_UNTIL.remove(player);
    }

    /** Custo por pacote de voz: uma busca em hash e, quando o prazo venceu, uma remocao. */
    public static boolean isMuted(UUID player) {
        Long until = MUTED_UNTIL.get(player);
        if (until == null) return false;
        if (until > System.currentTimeMillis()) return true;
        MUTED_UNTIL.remove(player, until);
        return false;
    }

    public static void clear() {
        MUTED_UNTIL.clear();
    }
}
