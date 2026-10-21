package com.aurorion.ethereal.house;

import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Intervalo minimo entre duas acoes de um jogador. Cada abertura de tela ou movimento do cofre custa
 * consulta e um pacote de volta; um cliente modificado mandando pacote (ou clique) por tick nao pode
 * transformar isso em carga.
 *
 * <p>Chave fraca: o ServerPlayer some do mapa quando sai ou renasce. Usado so no thread do servidor.
 */
public final class PlayerThrottle {
    private final long intervalMillis;
    private final Map<ServerPlayer, Long> last = new WeakHashMap<>();

    public PlayerThrottle(long intervalMillis) {
        this.intervalMillis = intervalMillis;
    }

    /** @return true se a acao deve ser ignorada; senao marca agora como a ultima. */
    public boolean throttled(ServerPlayer player) {
        long now = System.currentTimeMillis();
        Long previous = last.get(player);
        if (previous != null && now - previous < intervalMillis) return true;
        last.put(player, now);
        return false;
    }
}
