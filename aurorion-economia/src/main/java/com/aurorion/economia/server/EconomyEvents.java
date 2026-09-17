package com.aurorion.economia.server;

import com.aurorion.core.character.CharacterNamedEvent;
import com.aurorion.economia.AurorionEconomia;
import com.aurorion.economia.money.Money;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

@EventBusSubscriber(modid = AurorionEconomia.MOD_ID)
public final class EconomyEvents {
    private EconomyEvents() { }

    @SubscribeEvent
    public static void characterNamed(CharacterNamedEvent event) {
        if (!Wallet.grantStarter(event.player().server, event.character().id(), event.player().getUUID())) return;
        event.player().sendSystemMessage(Component.literal(
                "Sua nova história começa com " + Money.describe(Wallet.STARTER_BALANCE) + "."));
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
    }
}
