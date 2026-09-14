package com.aurorion.personagem.network;

import com.aurorion.personagem.AurorionPersonagem;
import com.aurorion.personagem.client.ClientCreation;
import com.aurorion.personagem.creation.CreationManager;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Tres pacotes, e o canal e {@code optional()}: um cliente sem o mod continua entrando no servidor.
 *
 * <p>Ele nao ve a tela — ve as mesmas perguntas no chat e responde em {@code /personagem criar}. A
 * regra nao muda: quem decide se o personagem existe e o servidor, com tela ou sem ela.
 */
@EventBusSubscriber(modid = AurorionPersonagem.MOD_ID)
public final class PersonagemNetwork {
    private PersonagemNetwork() {
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1").optional();

        registrar.playToClient(OpenCreationPayload.TYPE, OpenCreationPayload.STREAM_CODEC, (payload, context) -> {
            if (FMLEnvironment.dist == Dist.CLIENT) context.enqueueWork(() -> ClientCreation.open(payload));
        });
        registrar.playToClient(CreationFeedbackPayload.TYPE, CreationFeedbackPayload.STREAM_CODEC, (payload, context) -> {
            if (FMLEnvironment.dist == Dist.CLIENT) context.enqueueWork(() -> ClientCreation.feedback(payload));
        });
        registrar.playToServer(SubmitNamePayload.TYPE, SubmitNamePayload.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                context.enqueueWork(() -> CreationManager.submit(player, payload.firstName(), payload.lastName()));
            }
        });
    }
}
