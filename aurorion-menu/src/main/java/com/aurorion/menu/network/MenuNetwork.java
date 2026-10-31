package com.aurorion.menu.network;

import com.aurorion.menu.AurorionMenu;
import com.aurorion.menu.server.MenuPresence;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber(modid = AurorionMenu.MOD_ID)
public final class MenuNetwork {
    private static final String PROTOCOL_VERSION = "1";

    private MenuNetwork() {
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(PROTOCOL_VERSION)
                .playToServer(MenuPayloads.Request.TYPE, MenuPayloads.Request.STREAM_CODEC,
                        (payload, context) -> context.enqueueWork(() -> MenuPresence.request(context.player(), payload)))
                .playToClient(MenuPayloads.Presence.TYPE, MenuPayloads.Presence.STREAM_CODEC, (payload, context) -> {
                    if (FMLEnvironment.dist != Dist.CLIENT) return;
                    context.enqueueWork(() -> com.aurorion.menu.client.ClientPresence.accept(payload));
                });
    }
}
