package com.aurorion.magia.network;

import com.aurorion.magia.AurorionMagia;
import com.aurorion.magia.client.ClientSpellVisuals;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@EventBusSubscriber(modid = AurorionMagia.MOD_ID)
public final class MagiaNetwork {
    /** Versao do protocolo. Bump quando mudar o formato de algum payload. */
    private static final String PROTOCOL_VERSION = "1";

    private MagiaNetwork() {
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        // Sem optional(): o mod ja e obrigatorio no cliente por causa dos registros sincronizados.
        event.registrar(PROTOCOL_VERSION)
                .playToClient(SpellVisualPayload.TYPE, SpellVisualPayload.STREAM_CODEC, MagiaNetwork::handleVisual);
    }

    /**
     * So para quem esta rastreando o alvo (e o proprio alvo, se for jogador): quem esta fora da
     * distancia de rastreio nao veria o visual de qualquer jeito. Com 100 jogadores espalhados, o
     * pacote chega a uma duzia deles, nao aos 100.
     */
    public static void sendVisual(LivingEntity caster, LivingEntity target, SpellVisualPayload.Kind kind, int ttl) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(target,
                new SpellVisualPayload(kind, caster.getId(), target.getId(), ttl));
    }

    private static void handleVisual(SpellVisualPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) return;
        context.enqueueWork(() -> ClientSpellVisuals.accept(payload));
    }
}
