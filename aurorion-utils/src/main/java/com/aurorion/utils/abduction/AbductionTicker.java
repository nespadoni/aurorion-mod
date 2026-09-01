package com.aurorion.utils.abduction;

import com.aurorion.utils.AurorionUtils;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Avanca todas as abducoes em andamento, uma vez por tick. O custo e proporcional a quantas
 * abducoes estao ativas agora (tipicamente zero) — nunca a populacao do servidor, ao contrario de
 * um "por jogador por tick" — mesmo espirito do {@code CleanupScheduler} do aurorion-essentials.
 */
@EventBusSubscriber(modid = AurorionUtils.MOD_ID)
public final class AbductionTicker {
    private AbductionTicker() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        AbductionManager.tickAll(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        AbductionManager.clear();
    }
}
