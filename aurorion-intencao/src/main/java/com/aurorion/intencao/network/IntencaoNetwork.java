package com.aurorion.intencao.network;

import com.aurorion.intencao.AurorionIntencao;
import com.aurorion.intencao.client.ClientIntencao;
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

@EventBusSubscriber(modid = AurorionIntencao.MOD_ID)
public final class IntencaoNetwork {
    /** Bump quando mudar o formato de um payload ou a ordem de um enum que vai na rede. */
    private static final String PROTOCOL_VERSION = "1";

    private IntencaoNetwork() {
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        // Sem optional(): as entidades ja tornam o mod obrigatorio no cliente.
        event.registrar(PROTOCOL_VERSION)
                .playToClient(ScenePayload.TYPE, ScenePayload.STREAM_CODEC, IntencaoNetwork::handleScene)
                .playToClient(WorldPayload.TYPE, WorldPayload.STREAM_CODEC, IntencaoNetwork::handleWorld);
    }

    public static void sendScene(ServerPlayer player, ScenePayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    /** Visual preso a um ponto: vai para quem tem o chunk carregado. */
    public static void sendWorldAt(ServerLevel level, WorldPayload.Kind kind, Vec3 pos, int color, int ttl) {
        PacketDistributor.sendToPlayersTrackingChunk(level, new ChunkPos(BlockPos.containing(pos)),
                new WorldPayload(kind, -1, pos, color, ttl));
    }

    /** Um visual de mundo so para um jogador. */
    public static void sendWorldTo(ServerPlayer player, WorldPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    /** Visual preso a uma entidade: quem a rastreia, e ela mesma se for jogador. */
    public static void sendWorldOn(Entity entity, WorldPayload.Kind kind, int color, int ttl) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity,
                new WorldPayload(kind, entity.getId(), entity.position(), color, ttl));
    }

    private static void handleScene(ScenePayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) return;
        context.enqueueWork(() -> ClientIntencao.accept(payload));
    }

    private static void handleWorld(WorldPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) return;
        context.enqueueWork(() -> ClientIntencao.accept(payload));
    }
}
