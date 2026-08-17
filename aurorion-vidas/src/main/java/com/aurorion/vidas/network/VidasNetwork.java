package com.aurorion.vidas.network;

import com.aurorion.vidas.AurorionVidas;
import com.aurorion.vidas.client.ClientLives;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = AurorionVidas.MOD_ID)
public final class VidasNetwork {
    /** Versao do protocolo. Bump quando mudar o formato de algum payload. */
    private static final String PROTOCOL_VERSION = "1";

    private VidasNetwork() {
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        // optional(): um cliente vanilla entra normalmente, so nao ve os icones.
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION).optional();

        registrar.playToClient(SyncLivesPayload.TYPE, SyncLivesPayload.STREAM_CODEC, VidasNetwork::handleSync);
    }

    private static void handleSync(SyncLivesPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) return;
        context.enqueueWork(() -> ClientLives.accept(payload.lives(), payload.max()));
    }
}
