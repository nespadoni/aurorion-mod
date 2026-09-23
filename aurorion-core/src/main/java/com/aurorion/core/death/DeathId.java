package com.aurorion.core.death;

import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Um id por morte, o mesmo para todo mod que olhar para ela.
 *
 * <p>Antes, o historico do {@code aurorion_essentials} e o espolio do {@code aurorion_limbo} sorteavam
 * cada um o seu id para a mesma morte, e nao havia como a staff saber que o registro
 * {@code /deathhistory view X} era a morte cujo espolio o Relicario ja tinha chamado de volta. Com o
 * id vindo daqui, os dois falam da mesma coisa sem um importar o outro.
 *
 * <h2>Como "a mesma morte" e reconhecida</h2>
 *
 * <p>Tudo que acontece numa morte — o {@code LivingDeathEvent} em todas as prioridades e o
 * {@code LivingDropsEvent} — roda no mesmo tick, na mesma chamada de {@code die}. Entao o id e
 * guardado junto com o tick do servidor: quem pedir no mesmo tick recebe o mesmo, quem pedir num
 * tick depois recebe um novo. Uma morte cancelada (totem, PlayerRevive) e a morte de verdade que vem
 * depois do sangramento caem em ticks diferentes, e por isso nunca dividem o id.
 *
 * <p>A chave e a conta, nao a entidade: e uma entrada por conta que ja morreu desde o boot, trocada
 * no lugar a cada morte. Nada e varrido nem expira — nao ha trabalho fora da propria morte.
 *
 * <p><b>Thread</b>: so a do servidor, como os eventos que a chamam.
 */
public final class DeathId {
    private record Entry(UUID id, int tick) {
    }

    private static final Map<UUID, Entry> CURRENT = new HashMap<>();

    private DeathId() {
    }

    /** O id da morte que esta acontecendo agora com {@code player}. */
    public static UUID of(ServerPlayer player) {
        int tick = player.server.getTickCount();
        Entry entry = CURRENT.get(player.getUUID());
        if (entry != null && entry.tick() == tick) return entry.id();

        UUID id = UUID.randomUUID();
        CURRENT.put(player.getUUID(), new Entry(id, tick));
        return id;
    }

    /** Chamado pelo proprio core quando o servidor para. */
    public static void reset() {
        CURRENT.clear();
    }
}
