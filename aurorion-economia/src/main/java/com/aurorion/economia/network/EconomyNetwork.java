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

@EventBusSubscriber(modid = AurorionEconomia.MOD_ID)
public final class EconomyNetwork {
    private EconomyNetwork() { }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("2");
        registrar.playToServer(LandPayloads.Submit.TYPE, LandPayloads.Submit.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player)
                context.enqueueWork(() -> LandSaleManager.submit(player, payload));
        });
        registrar.playToServer(LandPayloads.Respond.TYPE, LandPayloads.Respond.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player)
                context.enqueueWork(() -> LandSaleManager.respond(player, payload.token(), payload.accept()));
        });
        registrar.playToClient(LandPayloads.Open.TYPE, LandPayloads.Open.CODEC, (payload, context) -> {
            if (FMLEnvironment.dist == Dist.CLIENT) context.enqueueWork(() -> EconomyClient.openLand(payload));
        });
        registrar.playToClient(LandPayloads.Approval.TYPE, LandPayloads.Approval.CODEC, (payload, context) -> {
            if (FMLEnvironment.dist == Dist.CLIENT) context.enqueueWork(() -> EconomyClient.approveLand(payload));
        });
        registrar.playToServer(EconomyPayloads.OpenRequest.TYPE, EconomyPayloads.OpenRequest.STREAM_CODEC,
                (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player)
                        context.enqueueWork(() -> ChargeManager.open(player, payload.target()));
                });
        registrar.playToServer(EconomyPayloads.MenuAction.TYPE, EconomyPayloads.MenuAction.STREAM_CODEC,
                (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player)
                        context.enqueueWork(() -> ChargeManager.select(player, payload.target(), payload.action()));
                });
        registrar.playToServer(EconomyPayloads.Submit.TYPE, EconomyPayloads.Submit.STREAM_CODEC,
                (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player)
                        context.enqueueWork(() -> ChargeManager.submit(player, payload.target(), payload.amount()));
                });
        registrar.playToServer(EconomyPayloads.Respond.TYPE, EconomyPayloads.Respond.STREAM_CODEC,
                (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player)
                        context.enqueueWork(() -> ChargeManager.respond(player, payload.token(), payload.accepted()));
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

    private static void client(Runnable action, net.neoforged.neoforge.network.handling.IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) context.enqueueWork(action);
    }
}
