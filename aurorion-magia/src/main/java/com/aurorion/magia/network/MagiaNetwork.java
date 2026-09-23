package com.aurorion.magia.network;

import com.aurorion.magia.AurorionMagia;
import com.aurorion.magia.client.ClientSpellVisuals;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

@EventBusSubscriber(modid = AurorionMagia.MOD_ID)
public final class MagiaNetwork {
    /** Versao do protocolo. Bump quando mudar o formato de algum payload ou a ordem dos Kind. */
    private static final String PROTOCOL_VERSION = "3";

    private MagiaNetwork() {
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        // Sem optional(): o mod ja e obrigatorio no cliente por causa dos registros sincronizados.
        event.registrar(PROTOCOL_VERSION)
                .playToClient(SpellVisualPayload.TYPE, SpellVisualPayload.STREAM_CODEC, MagiaNetwork::handleVisual);
    }

    /**
     * Visual preso ao alvo. Vai so para quem esta rastreando o alvo (e para ele mesmo, se for
     * jogador): quem esta fora da distancia de rastreio nao veria de qualquer jeito. Com 100
     * jogadores espalhados, chega a uma duzia deles, nao aos 100.
     */
    public static void sendVisual(@Nullable Entity caster, Entity target, SpellVisualPayload.Kind kind, int ttl) {
        sendVisual(caster, target, kind, ttl, target.position(), 0);
    }

    public static void sendVisual(@Nullable Entity caster, Entity target, SpellVisualPayload.Kind kind, int ttl,
                                  Vec3 pos, float extra) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(target,
                new SpellVisualPayload(kind, caster == null ? -1 : caster.getId(), target.getId(), ttl, pos, extra));
    }

    /** Visual preso a um ponto: vai para quem tem o chunk carregado. */
    public static void sendVisualAt(ServerLevel level, @Nullable Entity caster, SpellVisualPayload.Kind kind, int ttl,
                                    Vec3 pos, float extra) {
        PacketDistributor.sendToPlayersTrackingChunk(level, new ChunkPos(BlockPos.containing(pos)),
                new SpellVisualPayload(kind, caster == null ? -1 : caster.getId(), -1, ttl, pos, extra));
    }

    /** Um jogador so: reenvio de lacres ao entrar na dimensao, clarao de recusa. */
    public static void sendVisualTo(ServerPlayer player, SpellVisualPayload.Kind kind, int ttl, Vec3 pos, float extra) {
        PacketDistributor.sendToPlayer(player, new SpellVisualPayload(kind, -1, -1, ttl, pos, extra));
    }

    private static void handleVisual(SpellVisualPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) return;
        context.enqueueWork(() -> ClientSpellVisuals.accept(payload));
    }
}
