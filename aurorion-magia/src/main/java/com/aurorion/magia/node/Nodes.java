package com.aurorion.magia.node;

import com.aurorion.magia.network.NodePayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

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

    /**
     * Liga mais um no ali. O mesmo no no mesmo ponto nao duplica: e religado, com a abertura de novo.
     *
     * @return o no ligado, ou {@code null} se ja ha {@link Node#MAX_ACTIVE} ligados
     */
    @Nullable
    public static Node release(MinecraftServer server, int index, ServerLevel level, Vec3 pos) {
        NodeData data = NodeData.get(server);
        Node previous = null;
        for (Node node : data.all()) {
            if (node.sameSpot(index, level.dimension(), pos)) previous = node;
        }
        if (previous == null && data.size() >= Node.MAX_ACTIVE) return null;
        int id = previous != null ? previous.id() : data.nextId();
        Node node = new Node(id, index, level.dimension(), pos, server.overworld().getGameTime());
        data.put(node);
        broadcast(server);
        return node;
    }

    public static boolean end(MinecraftServer server, int id) {
        if (!NodeData.get(server).remove(id)) return false;
        broadcast(server);
        return true;
    }

    /**
     * O no {@code index} ligado mais perto de {@code from}, na mesma dimensao: o que o professor quer
     * desligar e o da aula dele, nao o de outra sala.
     */
    @Nullable
    public static Node nearest(MinecraftServer server, int index, ServerLevel level, Vec3 from) {
        Node best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Node node : NodeData.get(server).all()) {
            if (node.index() != index || !node.dimension().equals(level.dimension())) continue;
            double distance = node.pos().distanceToSqr(from);
            if (distance < bestDistance) {
                best = node;
                bestDistance = distance;
            }
        }
        return best;
    }

    public static int endAll(MinecraftServer server) {
        NodeData data = NodeData.get(server);
        List<Integer> ids = new ArrayList<>();
        for (Node node : data.all()) ids.add(node.id());
        for (int id : ids) data.remove(id);
        if (!ids.isEmpty()) broadcast(server);
        return ids.size();
    }

    public static void syncTo(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, payload(player.server));
    }

    private static void broadcast(MinecraftServer server) {
        PacketDistributor.sendToAllPlayers(payload(server));
    }

    private static NodePayload payload(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        NodeData data = NodeData.get(server);
        List<NodePayload.Entry> entries = new ArrayList<>(data.size());
        for (Node node : data.all()) {
            int age = (int) Math.min(MAX_AGE, Math.max(0, now - node.since()));
            entries.add(new NodePayload.Entry(node.index(), node.dimension().location(),
                    node.pos().x, node.pos().y, node.pos().z, age));
        }
        return new NodePayload(entries);
    }
}
