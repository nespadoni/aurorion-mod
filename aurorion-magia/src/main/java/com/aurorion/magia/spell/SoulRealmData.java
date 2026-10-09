package com.aurorion.magia.spell;

import com.aurorion.core.data.SavedDataAccess;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Retorno independente do NBT de jogador: sobrevive a morte, clone e restart. */
public final class SoulRealmData extends SavedData {
    private static final SavedDataAccess<SoulRealmData> ACCESS = new SavedDataAccess<>(
            "aurorion_magia_reino_almas", SoulRealmData::new, SoulRealmData::load);
    private final Map<UUID, CompoundTag> anchors = new HashMap<>();
    private long nextArena;

    public static SoulRealmData get(MinecraftServer server) { return ACCESS.get(server); }
    @Nullable
    public CompoundTag anchor(UUID player) { return anchors.get(player); }
    public void restoreAnchor(UUID player, CompoundTag anchor) { anchors.put(player, anchor); setDirty(); }
    public void remove(UUID player) { if (anchors.remove(player) != null) setDirty(); }

    @Nullable
    public Vec3 allocate() {
        // Separacao de 1024 blocos impede que expansoes de 200 alcancem outro duelo.
        // 16 mil celulas reutilizaveis; nunca reutiliza uma celula com participantes pendentes.
        for (int tries = 0; tries < 16_384; tries++) {
            long cell = Math.floorMod(nextArena++, 16_384L);
            Vec3 center = new Vec3((cell % 128) * 1024, 65, (cell / 128) * 1024);
            boolean occupied = anchors.values().stream().anyMatch(a ->
                    a.getDouble("ArenaX") == center.x && a.getDouble("ArenaZ") == center.z);
            if (!occupied) { setDirty(); return center; }
        }
        return null;
    }

    public void remember(ServerPlayer player, ServerPlayer peer, UUID duel, Vec3 arena, boolean owner) {
        CompoundTag a = new CompoundTag();
        a.putUUID("Player", player.getUUID());
        a.putUUID("Peer", peer.getUUID());
        a.putUUID("Duel", duel);
        a.putString("Dim", player.level().dimension().location().toString());
        a.putDouble("X", player.getX()); a.putDouble("Y", player.getY()); a.putDouble("Z", player.getZ());
        a.putFloat("Yaw", player.getYRot()); a.putFloat("Pitch", player.getXRot());
        a.putDouble("ArenaX", arena.x); a.putDouble("ArenaZ", arena.z);
        a.putBoolean("Owner", owner);
        anchors.put(player.getUUID(), a);
        setDirty();
    }

    private static SoulRealmData load(CompoundTag tag, HolderLookup.Provider registries) {
        SoulRealmData data = new SoulRealmData();
        data.nextArena = Math.max(0, tag.getLong("NextArena"));
        ListTag list = tag.getList("Anchors", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag a = list.getCompound(i);
            if (a.hasUUID("Player") && a.hasUUID("Peer") && a.hasUUID("Duel")) {
                data.anchors.put(a.getUUID("Player"), a);
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLong("NextArena", nextArena);
        ListTag list = new ListTag();
        anchors.values().forEach(a -> list.add(a.copy()));
        tag.put("Anchors", list);
        return tag;
    }
}
