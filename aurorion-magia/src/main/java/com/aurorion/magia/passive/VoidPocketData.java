package com.aurorion.magia.passive;

import com.aurorion.core.data.PlayerMapNbt;
import com.aurorion.core.data.SavedDataAccess;
import com.aurorion.magia.AurorionMagia;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * O que o bolso de Iris precisa lembrar mesmo depois de um crash: quem esta la dentro, de onde cada um
 * veio, quem ela marcou e onde fica a fenda.
 *
 * <p>Mesmo desenho do {@code SoulRealmData}: a ancora de retorno e do mundo, e nao do NBT do jogador,
 * para sobreviver a morte, ao clone e ao restart — ninguem fica preso numa replica da realidade porque o
 * servidor caiu.
 *
 * <ul>
 *   <li><b>Ancoras</b>, por pessoa dentro do bolso: dimensao e posicao de volta, o dono do bolso e o
 *       papel ({@code dona}, {@code marcado}, {@code invasor}).</li>
 *   <li><b>Sessoes</b>, por dona: os marcados (ate {@code maximoMarcados}), a fenda, o centro dentro do
 *       bolso, se a barreira caiu e a ancora fixa (com {@code espelhaPosicao = false}).</li>
 * </ul>
 */
public final class VoidPocketData extends SavedData {
    public static final String ROLE_OWNER = "dona";
    public static final String ROLE_GUEST = "marcado";
    public static final String ROLE_INVADER = "invasor";

    private static final SavedDataAccess<VoidPocketData> ACCESS = new SavedDataAccess<>(
            AurorionMagia.MOD_ID + "_bolso_vazio", VoidPocketData::new, VoidPocketData::load);

    private final Map<UUID, CompoundTag> anchors = new HashMap<>();
    private final Map<UUID, CompoundTag> sessions = new HashMap<>();

    public static VoidPocketData get(MinecraftServer server) {
        return ACCESS.get(server);
    }

    // --- Ancoras ---------------------------------------------------------------------------------

    @Nullable
    public CompoundTag anchor(UUID player) {
        return anchors.get(player);
    }

    public void remember(ServerPlayer player, UUID owner, String role) {
        CompoundTag a = new CompoundTag();
        a.putUUID("Owner", owner);
        a.putString("Role", role);
        a.putString("Dim", player.level().dimension().location().toString());
        a.putDouble("X", player.getX());
        a.putDouble("Y", player.getY());
        a.putDouble("Z", player.getZ());
        a.putFloat("Yaw", player.getYRot());
        a.putFloat("Pitch", player.getXRot());
        anchors.put(player.getUUID(), a);
        setDirty();
    }

    public void forget(UUID player) {
        if (anchors.remove(player) != null) setDirty();
    }

    /** Devolve uma ancora tirada antes de um retorno que outro mod vetou. */
    public void anchorAgain(UUID player, CompoundTag anchor) {
        anchors.put(player, anchor);
        setDirty();
    }

    /** Todos que estao no bolso desta dona, ela inclusive. */
    public List<UUID> members(UUID owner) {
        List<UUID> out = new ArrayList<>();
        anchors.forEach((player, a) -> {
            if (a.hasUUID("Owner") && a.getUUID("Owner").equals(owner)) out.add(player);
        });
        return out;
    }

    // --- Sessao ----------------------------------------------------------------------------------

    public CompoundTag session(UUID owner) {
        return sessions.computeIfAbsent(owner, ignored -> new CompoundTag());
    }

    public Set<UUID> marked(UUID owner) {
        Set<UUID> out = new LinkedHashSet<>();
        CompoundTag session = sessions.get(owner);
        if (session == null) return out;
        ListTag list = session.getList("Marked", Tag.TAG_INT_ARRAY);
        for (Tag entry : list) out.add(NbtUtils.loadUUID(entry));
        return out;
    }

    public void setMarked(UUID owner, Set<UUID> marked) {
        ListTag list = new ListTag();
        for (UUID id : marked) list.add(NbtUtils.createUUID(id));
        session(owner).put("Marked", list);
        setDirty();
    }

    /** Personagem novo: sem bolso, sem marcas. As ancoras de quem esta dentro ficam ate a volta. */
    public void clearSession(UUID owner) {
        if (sessions.remove(owner) != null) setDirty();
    }

    private static VoidPocketData load(CompoundTag tag, HolderLookup.Provider registries) {
        VoidPocketData data = new VoidPocketData();
        PlayerMapNbt.read(tag, "Anchors", data.anchors, VoidPocketData::strip);
        PlayerMapNbt.read(tag, "Sessions", data.sessions, VoidPocketData::strip);
        data.anchors.values().removeIf(a -> !a.hasUUID("Owner"));
        return data;
    }

    private static CompoundTag strip(CompoundTag entry) {
        CompoundTag copy = entry.copy();
        copy.remove(PlayerMapNbt.KEY_PLAYER);
        return copy;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("Anchors", PlayerMapNbt.write(anchors, VoidPocketData::copyInto));
        tag.put("Sessions", PlayerMapNbt.write(sessions, VoidPocketData::copyInto));
        return tag;
    }

    private static void copyInto(CompoundTag entry, CompoundTag value) {
        for (String key : value.getAllKeys()) {
            if (!PlayerMapNbt.KEY_PLAYER.equals(key)) entry.put(key, value.get(key).copy());
        }
    }
}
