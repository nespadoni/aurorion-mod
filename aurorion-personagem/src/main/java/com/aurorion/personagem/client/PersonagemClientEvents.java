package com.aurorion.personagem.client;

import com.aurorion.personagem.AurorionPersonagem;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = AurorionPersonagem.MOD_ID, value = Dist.CLIENT)
public final class PersonagemClientEvents {
    private PersonagemClientEvents() {
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        ClientCreation.tick();
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientCreation.clear();
    }
}
