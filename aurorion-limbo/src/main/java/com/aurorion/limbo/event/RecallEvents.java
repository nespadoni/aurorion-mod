package com.aurorion.limbo.event;

import com.aurorion.limbo.AurorionLimbo;
import com.aurorion.limbo.recall.DeathRecall;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Os ganchos do espolio: a morte, os drops e o relogio do Relicario.
 *
 * <p>Separado do {@link LimboServerEvents} porque nao tem nada a ver com exilio — vale para toda
 * morte fora do Limbo, de quem tem cinco vidas ou uma. Os itens que nao caem na morte sao regra do
 * core ({@code KeptOnDeath}), e por isso nao aparecem aqui.
 */
@EventBusSubscriber(modid = AurorionLimbo.MOD_ID)
public final class RecallEvents {
    private RecallEvents() {
    }

    /**
     * {@code LOWEST} e sem {@code receiveCanceled}: so a morte que todo mundo aceitou vira registro.
     * Um totem, um revive ou o bleeding do PlayerRevive cancelam antes, e ai nao houve morte.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            try {
                DeathRecall.recordDeath(player);
            } catch (RuntimeException error) {
                // Mesmo cuidado do LimboServerEvents#onDeath: a morte ja foi aceita, e uma falha no
                // registro do espolio nao pode interromper Entity#die no meio.
                AurorionLimbo.LOGGER.error("Falha ao registrar a morte de {} para o espolio.",
                        player.getGameProfile().getName(), error);
            }
        }
    }

    /**
     * A prioridade nao importa para o que chega: a lista e so guardada, e marcada no fim do tick com
     * tudo que os outros mods tiverem acrescentado. {@code LOWEST} so evita guardar a lista de uma
     * morte cujos drops outro mod ainda vai cancelar.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDrops(LivingDropsEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DeathRecall.onDrops(player, event.getDrops());
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        DeathRecall.tick(event.getServer());
    }

    /** Ainda com os jogadores conectados: da tempo de devolver o Relicario de um chamado aberto. */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        DeathRecall.abortAll(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        DeathRecall.reset();
    }
}
