package com.aurorion.talk.server;

import com.aurorion.talk.AurorionTalk;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber(modid = AurorionTalk.MOD_ID)
public final class TalkServerEvents {
    private TalkServerEvents() {
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            StyleManager.onPlayerJoin(player);
        }
    }
}
