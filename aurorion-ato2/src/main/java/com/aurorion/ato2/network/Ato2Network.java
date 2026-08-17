package com.aurorion.ato2.network;

import com.aurorion.ato2.AurorionAto2;
import com.aurorion.ato2.client.Ato2ClientNetwork;
import com.aurorion.ato2.house.HouseManager;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = AurorionAto2.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class Ato2Network {
    /** Versao do protocolo. Bump quando mudar o formato de algum payload. */
    private static final String PROTOCOL_VERSION = "1";

    private Ato2Network() {
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        // optional(): um cliente sem este mod nao deve ser barrado por causa do Ato 2 — ele so nao
        // consegue usar o altar. O mesmo vale ao remover o mod no fim do ato.
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION).optional();

        registrar.playToClient(OpenHouseSelectionPayload.TYPE, OpenHouseSelectionPayload.STREAM_CODEC, Ato2Network::handleOpen);
        registrar.playToClient(HouseChoiceResultPayload.TYPE, HouseChoiceResultPayload.STREAM_CODEC, Ato2Network::handleResult);
        registrar.playToServer(ChooseHousePayload.TYPE, ChooseHousePayload.STREAM_CODEC, Ato2Network::handleChoose);
    }

    // As duas pontas client-side so tocam em Ato2ClientNetwork depois do teste de dist: assim o
    // servidor dedicado nunca chega a carregar uma classe que importa Minecraft.
    private static void handleOpen(OpenHouseSelectionPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) return;
        Ato2ClientNetwork.openSelection(payload, context);
    }

    private static void handleResult(HouseChoiceResultPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) return;
        Ato2ClientNetwork.choiceResult(payload, context);
    }

    private static void handleChoose(ChooseHousePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        context.enqueueWork(() -> HouseManager.choose(player, payload.house()));
    }
}
