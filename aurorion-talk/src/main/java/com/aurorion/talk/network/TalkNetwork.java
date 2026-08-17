package com.aurorion.talk.network;

import com.aurorion.talk.AurorionTalk;
import com.aurorion.talk.client.ClientStyles;
import com.aurorion.talk.server.StyleManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.minecraft.server.level.ServerPlayer;

@EventBusSubscriber(modid = AurorionTalk.MOD_ID)
public final class TalkNetwork {
    /** Versao do protocolo. Bump quando mudar o formato de algum payload. */
    private static final String PROTOCOL_VERSION = "1";

    private TalkNetwork() {
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION).optional();

        registrar.playToClient(SyncStylesPayload.TYPE, SyncStylesPayload.STREAM_CODEC, TalkNetwork::handleSync);
        registrar.playToClient(UpdateStylePayload.TYPE, UpdateStylePayload.STREAM_CODEC, TalkNetwork::handleUpdate);
        registrar.playToServer(SetStylePayload.TYPE, SetStylePayload.STREAM_CODEC, TalkNetwork::handleSetStyle);
    }

    private static void handleSync(SyncStylesPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) return;
        context.enqueueWork(() -> ClientStyles.replaceAll(payload.styles()));
    }

    private static void handleUpdate(UpdateStylePayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) return;
        context.enqueueWork(() -> ClientStyles.put(payload.player(), payload.style().orElse(null)));
    }

    private static void handleSetStyle(SetStylePayload payload, IPayloadContext context) {
        // enqueueWork already hands us back the main server thread; player() gives the sender.
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer sender) {
                StyleManager.requestStyle(sender, payload.style());
            }
        });
    }
}
