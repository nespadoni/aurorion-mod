package com.aurorion.diario.network;

import com.aurorion.diario.AurorionDiario;
import com.aurorion.diario.server.DiaryServer;
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
 * Canal do diário. Opcional: cliente sem o mod entra no servidor normalmente; o {@code /diario}
 * só manda o link do site para ele.
 *
 * <p>As classes de cliente são citadas pelo nome completo só dentro do teste de {@link Dist}
 * (SDD §3.1, §13.5): o servidor dedicado nunca as carrega.
 */
@EventBusSubscriber(modid = AurorionDiario.MOD_ID)
public final class DiaryNetwork {
    /** Muda junto com o formato dos pacotes: cliente de outra versão fica sem o canal e recebe o link do site. */
    private static final String PROTOCOL_VERSION = "2";

    private DiaryNetwork() {
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION).optional();

        registrar.playToServer(DiaryPayloads.Request.TYPE, DiaryPayloads.Request.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) context.enqueueWork(() -> DiaryServer.request(player, payload));
        });
        registrar.playToServer(DiaryPayloads.Save.TYPE, DiaryPayloads.Save.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) context.enqueueWork(() -> DiaryServer.save(player, payload));
        });
        registrar.playToServer(DiaryPayloads.Publish.TYPE, DiaryPayloads.Publish.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) context.enqueueWork(() -> DiaryServer.publish(player, payload));
        });

        registrar.playToClient(DiaryPayloads.Open.TYPE, DiaryPayloads.Open.STREAM_CODEC, (payload, context) -> {
            if (FMLEnvironment.dist == Dist.CLIENT) context.enqueueWork(() -> com.aurorion.diario.client.DiaryClient.open(payload));
        });
        registrar.playToClient(DiaryPayloads.Entry.TYPE, DiaryPayloads.Entry.STREAM_CODEC, (payload, context) -> {
            if (FMLEnvironment.dist == Dist.CLIENT) context.enqueueWork(() -> com.aurorion.diario.client.DiaryClient.entry(payload));
        });
        registrar.playToClient(DiaryPayloads.Status.TYPE, DiaryPayloads.Status.STREAM_CODEC, (payload, context) -> {
            if (FMLEnvironment.dist == Dist.CLIENT) context.enqueueWork(() -> com.aurorion.diario.client.DiaryClient.status(payload));
        });
        registrar.playToClient(DiaryPayloads.Conflict.TYPE, DiaryPayloads.Conflict.STREAM_CODEC, (payload, context) -> {
            if (FMLEnvironment.dist == Dist.CLIENT) context.enqueueWork(() -> com.aurorion.diario.client.DiaryClient.conflict(payload));
        });
    }

    /** O cliente tem a tela do diário? */
    public static boolean canOpen(ServerPlayer player) {
        return player.connection != null && player.connection.hasChannel(DiaryPayloads.Open.TYPE);
    }

    /** Só para quem tem o canal: cliente sem o mod não recebe pacote desconhecido. */
    public static void send(ServerPlayer player, CustomPacketPayload payload) {
        if (player.connection != null && player.connection.hasChannel(payload.type())) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }
}
