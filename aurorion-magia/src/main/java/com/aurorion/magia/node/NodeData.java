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
 * Os nos liberados. Persistido: a cena so sai quando a staff desliga; nem reinicio do servidor a apaga.
 */
public final class NodeData extends SavedData {
    private static final SavedDataAccess<NodeData> ACCESS =
            new SavedDataAccess<>(AurorionMagia.MOD_ID + "_nos", NodeData::new, NodeData::load);

    private final Map<Integer, Node> nodes = new TreeMap<>();

    public static NodeData get(MinecraftServer server) {
        return ACCESS.get(server);
    }

    private static NodeData load(CompoundTag tag, HolderLookup.Provider registries) {
        NodeData data = new NodeData();
        ListTag list = tag.getList("Nos", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            Node node = Node.load(list.getCompound(i));
            if (node != null) data.nodes.put(node.index(), node);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Node node : nodes.values()) list.add(node.save());
        tag.put("Nos", list);
        return tag;
    }

    public Collection<Node> all() {
        return Collections.unmodifiableCollection(nodes.values());
    }

    @Nullable
    public Node get(int index) {
        return nodes.get(index);
    }

    void put(Node node) {
        nodes.put(node.index(), node);
        setDirty();
    }

    boolean remove(int index) {
        if (nodes.remove(index) == null) return false;
        setDirty();
        return true;
    }
}
