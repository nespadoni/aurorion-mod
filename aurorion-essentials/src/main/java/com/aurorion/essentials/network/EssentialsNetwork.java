package com.aurorion.essentials.network;

import com.aurorion.essentials.AurorionEssentials;
import com.aurorion.essentials.fakename.FakeName;
import com.aurorion.essentials.fakename.FakeNameRegistry;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = AurorionEssentials.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class EssentialsNetwork {
    /** Versao do protocolo. Bump quando mudar o formato de algum payload. */
    private static final String PROTOCOL_VERSION = "1";

    private EssentialsNetwork() {
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION).optional();

        registrar.playToClient(SyncFakeNamesPayload.TYPE, SyncFakeNamesPayload.STREAM_CODEC, EssentialsNetwork::handleSync);
        registrar.playToClient(UpdateFakeNamePayload.TYPE, UpdateFakeNamePayload.STREAM_CODEC, EssentialsNetwork::handleUpdate);
    }

    private static void handleSync(SyncFakeNamesPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) return;
        context.enqueueWork(() -> {
            FakeNameRegistry.clear();
            payload.names().forEach((uuid, raw) -> FakeNameRegistry.put(uuid, FakeName.parse(raw)));
        });
    }

    private static void handleUpdate(UpdateFakeNamePayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) return;
        context.enqueueWork(() -> {
            if (payload.name().isPresent()) {
                FakeNameRegistry.put(payload.player(), FakeName.parse(payload.name().get()));
            } else {
                FakeNameRegistry.remove(payload.player());
            }
        });
    }
}
