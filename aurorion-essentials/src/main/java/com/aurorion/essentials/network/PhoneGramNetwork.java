package com.aurorion.essentials.network;

import com.aurorion.essentials.AurorionEssentials;
import com.aurorion.essentials.compat.PhoneGramImageStore;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Canal das fotos do Gram sob demanda, separado do {@link EssentialsNetwork} para ter versao propria.
 * Opcional como o resto: cliente sem o essentials nao registra o canal, e o servidor manda a esse
 * cliente as fotos inteiras, como o telefone sempre fez.
 */
@EventBusSubscriber(modid = AurorionEssentials.MOD_ID)
public final class PhoneGramNetwork {
    /** Versao do protocolo. Bump quando mudar o formato de algum payload. */
    private static final String PROTOCOL_VERSION = "1";

    private PhoneGramNetwork() {
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION).optional();
        registrar.playToServer(PhoneGramImagePayloads.Request.TYPE, PhoneGramImagePayloads.Request.STREAM_CODEC,
                PhoneGramNetwork::handleRequest);
        registrar.playToClient(PhoneGramImagePayloads.Image.TYPE, PhoneGramImagePayloads.Image.STREAM_CODEC,
                PhoneGramNetwork::handleImage);
    }

    private static void handleRequest(PhoneGramImagePayloads.Request payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) PhoneGramImageStore.request(player, payload.hash());
        });
    }

    private static void handleImage(PhoneGramImagePayloads.Image payload, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) return;
        context.enqueueWork(() -> com.aurorion.essentials.client.PhoneGramMedia.receive(payload.hash(), payload.bytes()));
    }
}
