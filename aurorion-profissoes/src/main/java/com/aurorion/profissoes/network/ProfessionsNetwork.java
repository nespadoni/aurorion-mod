package com.aurorion.profissoes.network;

import com.aurorion.profissoes.AurorionProfissoes;
import com.aurorion.profissoes.api.ProfessionApi;
import com.aurorion.profissoes.data.Profession;
import com.aurorion.profissoes.server.ServiceManager;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber(modid = AurorionProfissoes.MOD_ID)
public final class ProfessionsNetwork {
    private ProfessionsNetwork() {}
    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(ActionPayload.TYPE, ActionPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) context.enqueueWork(() -> ServiceManager.action(player, payload));
        });
        registrar.playToClient(PanelPayload.TYPE, PanelPayload.STREAM_CODEC, (payload, context) -> {
            if (FMLEnvironment.dist == Dist.CLIENT) context.enqueueWork(() -> com.aurorion.profissoes.client.ProfessionClient.open(payload));
        });
        registrar.playToClient(ProfessionPayload.TYPE, ProfessionPayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> ProfessionApi.clientProfession = Profession.parse(payload.profession())));
    }
    public static void sync(ServerPlayer player) {
        if (player.connection.hasChannel(ProfessionPayload.TYPE.id()))
            PacketDistributor.sendToPlayer(player, new ProfessionPayload(ProfessionApi.of(player).id()));
    }
}
