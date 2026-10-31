package com.aurorion.magia.node;

import com.aurorion.magia.network.NodePayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Liga, desliga e conta aos clientes. O servidor nao roda nada por tick para os nos: a cena inteira —
 * circulo, aura, fogo, raios, sussurros, escuridao — e desenhada por cada cliente a partir da posicao.
 */
public final class Nodes {
    /** Teto da idade enviada: passada a abertura, tanto faz se faz uma hora ou um mes. */
    private static final int MAX_AGE = 1_000_000;

    private Nodes() {
    }

    public static void release(MinecraftServer server, int index, ServerLevel level, Vec3 pos) {
        NodeData.get(server).put(new Node(index, level.dimension(), pos, server.overworld().getGameTime()));
        broadcast(server);
    }

    public static boolean end(MinecraftServer server, int index) {
        if (!NodeData.get(server).remove(index)) return false;
        broadcast(server);
        return true;
    }

    public static void syncTo(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, payload(player.server));
    }

    private static void broadcast(MinecraftServer server) {
        PacketDistributor.sendToAllPlayers(payload(server));
    }

    private static NodePayload payload(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        List<NodePayload.Entry> entries = new ArrayList<>(Node.COUNT);
        for (Node node : NodeData.get(server).all()) {
            int age = (int) Math.min(MAX_AGE, Math.max(0, now - node.since()));
            entries.add(new NodePayload.Entry(node.index(), node.dimension().location(),
                    node.pos().x, node.pos().y, node.pos().z, age));
        }
        return new NodePayload(entries);
    }
}
