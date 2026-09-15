package com.aurorion.areas.network;

import com.aurorion.areas.AurorionAreas;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber(modid = AurorionAreas.MOD_ID)
public final class AreaNetwork {
    private AreaNetwork() {}
    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").optional().playToClient(AreaStatePayload.TYPE, AreaStatePayload.STREAM_CODEC,
                (payload, context) -> {
                    if (FMLEnvironment.dist == Dist.CLIENT)
                        context.enqueueWork(() -> com.aurorion.areas.client.AreaClient.accept(payload));
                });
    }
    public static void send(ServerPlayer player, AreaStatePayload state) {
        if (player.connection.hasChannel(AreaStatePayload.TYPE.id())) PacketDistributor.sendToPlayer(player, state);
    }
}
