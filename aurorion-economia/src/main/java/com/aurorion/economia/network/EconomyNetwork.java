package com.aurorion.economia.network;

import com.aurorion.economia.AurorionEconomia;
import com.aurorion.economia.client.EconomyClient;
import com.aurorion.economia.server.ChargeManager;
import com.aurorion.economia.server.LandSaleManager;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.HandlerThread;

@EventBusSubscriber(modid = AurorionEconomia.MOD_ID)
public final class EconomyNetwork {
    private EconomyNetwork() { }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("2");
        var serverRegistrar = registrar.executesOn(HandlerThread.NETWORK);
        serverRegistrar.playToServer(LandPayloads.Submit.TYPE, LandPayloads.Submit.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player && ChargeManager.admitPacket(player, ChargeManager.PacketAction.LAND_SUBMIT))
                server(player, () -> LandSaleManager.submit(player, payload), context);
        });
        serverRegistrar.playToServer(LandPayloads.Respond.TYPE, LandPayloads.Respond.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player && ChargeManager.admitPacket(player, ChargeManager.PacketAction.LAND_RESPOND))
                server(player, () -> LandSaleManager.respond(player, payload.token(), payload.accept()), context);
        });
        registrar.playToClient(LandPayloads.Open.TYPE, LandPayloads.Open.CODEC, (payload, context) -> {
            if (FMLEnvironment.dist == Dist.CLIENT) context.enqueueWork(() -> EconomyClient.openLand(payload));
        });
        registrar.playToClient(LandPayloads.Approval.TYPE, LandPayloads.Approval.CODEC, (payload, context) -> {
            if (FMLEnvironment.dist == Dist.CLIENT) context.enqueueWork(() -> EconomyClient.approveLand(payload));
        });
        serverRegistrar.playToServer(EconomyPayloads.OpenRequest.TYPE, EconomyPayloads.OpenRequest.STREAM_CODEC,
                (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player && ChargeManager.admitPacket(player, ChargeManager.PacketAction.OPEN))
                        server(player, () -> ChargeManager.open(player, payload.target()), context);
                });
        serverRegistrar.playToServer(EconomyPayloads.MenuAction.TYPE, EconomyPayloads.MenuAction.STREAM_CODEC,
                (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player && ChargeManager.admitPacket(player, ChargeManager.PacketAction.SELECT))
                        server(player, () -> ChargeManager.select(player, payload.target(), payload.action()), context);
                });
        serverRegistrar.playToServer(EconomyPayloads.Submit.TYPE, EconomyPayloads.Submit.STREAM_CODEC,
                (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player && ChargeManager.admitPacket(player, ChargeManager.PacketAction.SUBMIT))
                        server(player, () -> ChargeManager.submit(player, payload.target(), payload.amount()), context);
                });
        serverRegistrar.playToServer(EconomyPayloads.Respond.TYPE, EconomyPayloads.Respond.STREAM_CODEC,
                (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player && ChargeManager.admitPacket(player, ChargeManager.PacketAction.RESPOND))
                        server(player, () -> ChargeManager.respond(player, payload.token(), payload.accepted()), context);
                });

        registrar.playToClient(EconomyPayloads.OpenMenu.TYPE, EconomyPayloads.OpenMenu.STREAM_CODEC,
                (payload, context) -> client(() -> EconomyClient.openMenu(payload), context));
        registrar.playToClient(EconomyPayloads.OpenComposer.TYPE, EconomyPayloads.OpenComposer.STREAM_CODEC,
                (payload, context) -> client(() -> EconomyClient.openComposer(payload), context));
        registrar.playToClient(EconomyPayloads.OpenApproval.TYPE, EconomyPayloads.OpenApproval.STREAM_CODEC,
                (payload, context) -> client(() -> EconomyClient.openApproval(payload), context));
        registrar.playToClient(EconomyPayloads.Status.TYPE, EconomyPayloads.Status.STREAM_CODEC,
                (payload, context) -> client(() -> EconomyClient.openStatus(payload), context));
    }

    private static void server(ServerPlayer player, Runnable action,
                               net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!player.hasDisconnected() && player.server.getPlayerList().getPlayer(player.getUUID()) == player)
                action.run();
        });
    }

    private static void client(Runnable action, net.neoforged.neoforge.network.handling.IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) context.enqueueWork(action);
    }
}
