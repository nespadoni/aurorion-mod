package com.aurorion.essentials.death;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Itens de um registro que ja sairam (ou estao saindo) nesta sessao do servidor. So na thread do servidor.
 *
 * <p>O recibo em disco ({@code restores/<id>.nbt}) e a verdade que sobrevive ao reinicio, mas e escrito
 * na fila de IO. A retirada no criativo acontece no clique, sem esperar disco; sem esta trava, um
 * {@code devolver} com a reserva ainda na fila e um clique na tela entregariam o mesmo item duas vezes.
 * Todo caminho que entrega item marca aqui antes de ir ao disco e confere aqui antes de entregar.
 *
 * <p>Uma entrega que termina {@code ABORTED} (nada saiu) devolve os indices. {@code FAILED} e
 * {@code RESERVED} ficam marcados, igual ao recibo. Zera ao parar o servidor.
 */
final class RecoveryLedger {
    /** Indice especial: restauracao completa em andamento ou feita. */
    static final int FULL = -1;

    private static final Map<UUID, Set<Integer>> CLAIMED = new HashMap<>();

    private RecoveryLedger() { }

    static boolean claimed(UUID id, int item) {
        Set<Integer> claimed = CLAIMED.get(id);
        return claimed != null && (claimed.contains(item) || claimed.contains(FULL));
    }

    /** Marca todos ou nenhum. {@code false} se algum ja estava marcado. */
    static boolean claim(UUID id, int... items) {
        Set<Integer> claimed = CLAIMED.computeIfAbsent(id, ignored -> new HashSet<>());
        if (claimed.contains(FULL)) return false;
        for (int item : items) {
            if (item == FULL ? !claimed.isEmpty() : claimed.contains(item)) return false;
        }
        for (int item : items) claimed.add(item);
        return true;
    }

    static void release(UUID id, int... items) {
        Set<Integer> claimed = CLAIMED.get(id);
        if (claimed == null) return;
        for (int item : items) claimed.remove(item);
        if (claimed.isEmpty()) CLAIMED.remove(id);
    }

    static void clear() {
        CLAIMED.clear();
    }
}
