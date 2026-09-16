package com.aurorion.areas.network;

import com.aurorion.areas.AurorionAreas;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
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
        var registrar = event.registrar("1").optional();
        registrar.playToClient(AreaStatePayload.TYPE, AreaStatePayload.STREAM_CODEC,
                (payload, context) -> {
                    if (FMLEnvironment.dist == Dist.CLIENT)
                        context.enqueueWork(() -> com.aurorion.areas.client.AreaClient.accept(payload));
                });
        registrar.playToClient(AreaOutlinePayload.TYPE, AreaOutlinePayload.STREAM_CODEC,
                (payload, context) -> {
                    if (FMLEnvironment.dist == Dist.CLIENT)
                        context.enqueueWork(() -> com.aurorion.areas.client.AreaOutlineRenderer.accept(payload));
                });
    }
    public static void send(ServerPlayer player, CustomPacketPayload state) {
        if (player.connection.hasChannel(state.type().id())) PacketDistributor.sendToPlayer(player, state);
    }
    /** Quem nao tem o modulo cliente nao recebe o contorno; quem pediu precisa saber disso. */
    public static boolean hasOutlineChannel(ServerPlayer player) {
        return player.connection.hasChannel(AreaOutlinePayload.TYPE.id());
    }
}
