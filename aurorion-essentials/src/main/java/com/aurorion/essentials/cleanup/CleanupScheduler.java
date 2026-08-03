package com.aurorion.essentials.cleanup;

import com.aurorion.essentials.AurorionEssentials;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Conta ticks do servidor (nao game time do mundo, que pode parar com {@code doDaylightCycle
 * false}) e dispara o aviso e a limpeza no momento certo.
 *
 * <p>O custo em todo tick "de espera" e um incremento de {@code long} e duas comparacoes — sem
 * alocar nada, sem tocar em entidade nenhuma. O trabalho de verdade ({@link EntityCleanup#run})
 * so roda no tick exato em que o intervalo fecha, no maximo 1x por hora por padrao — nunca por
 * tick, nunca por jogador.</p>
 *
 * <p>O intervalo e o aviso sao recalculados a partir da {@link CleanupConfig} a cada tick (so
 * aritmetica de inteiro, sem alocacao), entao editar o arquivo de config aplica no ciclo seguinte
 * sem precisar reiniciar o servidor.</p>
 */
@EventBusSubscriber(modid = AurorionEssentials.MOD_ID)
public final class CleanupScheduler {
    private static final long TICKS_PER_SECOND = 20L;

    private static long ticksSinceLastCleanup = 0;
    private static boolean warned = false;

    private CleanupScheduler() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!CleanupConfig.ENABLED.get()) return;

        long intervalTicks = CleanupConfig.INTERVAL_MINUTES.get() * 60L * TICKS_PER_SECOND;
        long warningTicks = Math.min(CleanupConfig.WARNING_SECONDS_BEFORE.get() * TICKS_PER_SECOND, intervalTicks);

        ticksSinceLastCleanup++;

        if (!warned && ticksSinceLastCleanup >= intervalTicks - warningTicks) {
            EntityCleanup.warn(event.getServer());
            warned = true;
        }

        if (ticksSinceLastCleanup >= intervalTicks) {
            EntityCleanup.run(event.getServer());
            ticksSinceLastCleanup = 0;
            warned = false;
        }
    }
}
