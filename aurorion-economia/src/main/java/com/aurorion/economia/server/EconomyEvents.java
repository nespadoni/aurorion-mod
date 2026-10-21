package com.aurorion.economia.server;

import com.aurorion.core.character.CharacterNamedEvent;
import com.aurorion.economia.AurorionEconomia;
import com.aurorion.economia.money.Money;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = AurorionEconomia.MOD_ID)
public final class EconomyEvents {
    /** Um minuto: salario e contado em dias, precisao maior so gastaria tick. */
    private static final int SALARY_CHECK_TICKS = 1200;

    private EconomyEvents() { }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % SALARY_CHECK_TICKS == 0) {
            try (var origin = MoneyLedger.origin("casa:salario")) {
                HouseTreasury.paySalaries(event.getServer());
            }
        }
        EconomyProjectorNotifier.tick(event.getServer());
        WalletData.flushIfDue(event.getServer());
    }

    @SubscribeEvent
    public static void characterNamed(CharacterNamedEvent event) {
        if (!Wallet.grantStarter(event.player().server, event.character().id(), event.player().getUUID())) return;
        event.player().sendSystemMessage(Component.literal(
                "Sua nova história começa com " + Money.describe(Wallet.STARTER_BALANCE)
                        + ". Ela pode ser gasta desde já; repassar a outra pessoa, só depois de "
                        + WalletData.STARTER_LOCK_TICKS / 72_000L + " horas de jogo."));
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        ChargeManager.forget(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        ChargeManager.forget(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void stop(ServerStoppedEvent event) {
        ChargeManager.clear();
        EconomyProjectorNotifier.clear();
        // O que a thread do livro-caixa ainda nao gravou vai agora, antes de o processo sair.
        MoneyLedger.drain();
    }
}
