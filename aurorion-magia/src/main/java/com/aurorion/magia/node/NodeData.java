package com.aurorion.magia.node;

import com.aurorion.core.data.SavedDataAccess;
import com.aurorion.magia.AurorionMagia;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * Os nos liberados, por {@code id}. Persistido: a cena so sai quando a staff desliga; nem reinicio do
 * servidor a apaga.
 */
public final class NodeData extends SavedData {
    private static final SavedDataAccess<NodeData> ACCESS =
            new SavedDataAccess<>(AurorionMagia.MOD_ID + "_nos", NodeData::new, NodeData::load);

    private final Map<Integer, Node> nodes = new TreeMap<>();
    /** Proximo {@code id}. Nao volta para tras: um numero desligado nao e reaproveitado no mesmo mundo. */
    private int nextId = 1;

    public static NodeData get(MinecraftServer server) {
        return ACCESS.get(server);
    }

    private static NodeData load(CompoundTag tag, HolderLookup.Provider registries) {
        NodeData data = new NodeData();
        data.nextId = Math.max(1, tag.getInt("Proximo"));
        ListTag list = tag.getList("Nos", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            Node node = Node.load(list.getCompound(i), data.nextId);
            if (node == null || data.nodes.containsKey(node.id())) continue;
            data.nodes.put(node.id(), node);
            data.nextId = Math.max(data.nextId, node.id() + 1);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Node node : nodes.values()) list.add(node.save());
        tag.put("Nos", list);
        tag.putInt("Proximo", nextId);
        return tag;
    }

    public Collection<Node> all() {
        return Collections.unmodifiableCollection(nodes.values());
    }

    @Nullable
    public Node get(int id) {
        return nodes.get(id);
    }

    public int size() {
        return nodes.size();
    }

    int nextId() {
        return nextId++;
    }

    void put(Node node) {
        nodes.put(node.id(), node);
        setDirty();
    }

    boolean remove(int id) {
        if (nodes.remove(id) == null) return false;
        setDirty();
        return true;
    }
}
