package com.aurorion.servicos.network;

import com.aurorion.servicos.AurorionServicos;
import com.aurorion.servicos.server.ServicosManager;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Canal do app. Opcional: cliente sem o mod entra no servidor normalmente, so nao tem o app.
 *
 * <p>A classe de cliente e citada pelo nome completo so dentro do teste de {@link Dist}, como nos
 * outros mods (SDD §3.1): o servidor dedicado nunca a carrega.
 */
@EventBusSubscriber(modid = AurorionServicos.MOD_ID)
public final class ServicosNetwork {
    private static final String PROTOCOL_VERSION = "1";

    private ServicosNetwork() {
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION).optional();

        registrar.playToServer(ServicosQueryPayload.TYPE, ServicosQueryPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) context.enqueueWork(() -> ServicosManager.query(player, payload));
        });
        registrar.playToServer(ServicosActionPayload.TYPE, ServicosActionPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) context.enqueueWork(() -> ServicosManager.action(player, payload));
        });
        registrar.playToClient(ServicosPagePayload.TYPE, ServicosPagePayload.STREAM_CODEC, (payload, context) -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                context.enqueueWork(() -> com.aurorion.servicos.client.ServicosClient.receive(payload));
            }
        });
        registrar.playToClient(ServicosNotifyPayload.TYPE, ServicosNotifyPayload.STREAM_CODEC, (payload, context) -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                context.enqueueWork(() -> com.aurorion.servicos.client.ServicosClient.notify(payload));
            }
        });
    }

    /** So para quem tem o canal: cliente sem o mod nao recebe pacote desconhecido. */
    public static void send(ServerPlayer player, CustomPacketPayload payload) {
        if (player.connection != null && player.connection.hasChannel(payload.type())) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }
}
