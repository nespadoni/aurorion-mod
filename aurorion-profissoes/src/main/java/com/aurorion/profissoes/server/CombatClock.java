package com.aurorion.profissoes.server;

import com.aurorion.profissoes.AurorionProfissoes;
import net.minecraft.Util;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Quando cada jogador tomou (ou causou) dano pela ultima vez.
 *
 * <p>Existe para a trava de combate da cura do medico: sem ela, um medico no meio de uma luta
 * restaurava o aliado inteiro a cada troca de golpes e o combate deixava de ter consequencia.
 * Quem bate tambem conta como "em combate" — senao o atacante se curava entre um golpe e outro.</p>
 *
 * <p>Relogio monotono ({@link Util#getMillis()}), em memoria, por conta. Nao zera no logout: sair e
 * voltar nao pode ser o jeito de escapar da trava. O mapa e varrido quando cresce; com 80 pessoas
 * ele fica do tamanho de quem brigou nos ultimos segundos.</p>
 */
@EventBusSubscriber(modid = AurorionProfissoes.MOD_ID)
public final class CombatClock {
    private static final int SWEEP_AT = 1024;
    /** Entradas mais velhas que isto nao servem a nenhuma trava e podem sair na varredura. */
    private static final long KEEP_MS = 120_000L;
    private static final Map<UUID, Long> LAST_DAMAGE = new HashMap<>();

    private CombatClock() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void damaged(LivingDamageEvent.Post event) {
        if (event.getNewDamage() <= 0) return;
        long now = Util.getMillis();
        if (event.getEntity() instanceof ServerPlayer victim && !(victim instanceof FakePlayer)) mark(victim.getUUID(), now);
        if (event.getSource().getEntity() instanceof ServerPlayer attacker && !(attacker instanceof FakePlayer)
                && attacker != event.getEntity()) mark(attacker.getUUID(), now);
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        LAST_DAMAGE.clear();
    }

    private static void mark(UUID player, long now) {
        if (LAST_DAMAGE.size() >= SWEEP_AT) LAST_DAMAGE.values().removeIf(at -> now - at > KEEP_MS);
        LAST_DAMAGE.put(player, now);
    }

    /** @return milissegundos que ainda faltam para a trava de {@code windowMs} acabar; 0 se livre. */
    public static long lockedFor(ServerPlayer player, long windowMs) {
        Long at = LAST_DAMAGE.get(player.getUUID());
        if (at == null) return 0L;
        return Math.max(0L, at + windowMs - Util.getMillis());
    }
}
