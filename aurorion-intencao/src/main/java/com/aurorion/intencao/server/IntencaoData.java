package com.aurorion.intencao.server;

import com.aurorion.core.data.SavedDataAccess;
import com.aurorion.intencao.network.Tone;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Sessoes abertas e ancoras de retorno. Fora do NBT do jogador, como o Reino das Almas: sobrevive a
 * morte, clone, relog e restart, e e a <b>unica</b> coisa que sabe para onde cada um volta.
 *
 * <p>Uma ancora vive mais que a sessao de proposito: quem estava offline no veredito volta ao entrar,
 * pela ancora, mesmo com a sessao ja apagada.
 */
public final class IntencaoData extends SavedData {
    private static final SavedDataAccess<IntencaoData> ACCESS = new SavedDataAccess<>(
            "aurorion_intencao_sessoes", IntencaoData::new, IntencaoData::load);
    /** 1024 blocos entre arenas: nenhuma estrutura de tema alcanca a vizinha. */
    private static final int CELL_SPACING = 1024;
    private static final int GRID = 64;
    public static final int FLOOR_Y = 64;

    public enum State { ACTIVE, ENDING }

    public static final class Session {
        public final UUID id;
        public final ResourceLocation theme;
        public final BlockPos center;
        /** Ordem fixa: o indice define o lado da arena e a cor. */
        public final List<UUID> members;
        public final List<Integer> colors;
        /** Nome do personagem no inicio: o HUD mostra quem esta na mesa mesmo com alguem offline. */
        public final List<String> names;
        /** 0 = "jogadores", 1 = "rivais" no comando. O veredito e por lado: o time inteiro vence junto. */
        public final List<Integer> sides;
        public final long startedAt;
        public Tone tone;
        public State state;

        Session(UUID id, ResourceLocation theme, BlockPos center, List<UUID> members, List<Integer> colors,
                List<String> names, List<Integer> sides, long startedAt, Tone tone, State state) {
            this.id = id;
            this.theme = theme;
            this.center = center;
            this.members = List.copyOf(members);
            this.colors = List.copyOf(colors);
            this.names = List.copyOf(names);
            this.sides = List.copyOf(sides);
            this.startedAt = startedAt;
            this.tone = tone;
            this.state = state;
        }

        public int indexOf(UUID player) {
            return members.indexOf(player);
        }

        /** O lado do participante, ou -1 se ele nao esta na sessao. */
        public int sideOf(UUID player) {
            int index = members.indexOf(player);
            return index < 0 ? -1 : sides.get(index);
        }
    }

    /** Para onde volta, e qual Eco ficou no lugar. */
    public record Anchor(UUID session, ResourceLocation dimension, Vec3 pos, float yaw, float pitch,
                         @Nullable UUID echo) {
        Anchor withEcho(@Nullable UUID value) {
            return new Anchor(session, dimension, pos, yaw, pitch, value);
        }
    }

    private final Map<UUID, Session> sessions = new LinkedHashMap<>();
    private final Map<UUID, Anchor> anchors = new LinkedHashMap<>();

    public static IntencaoData get(MinecraftServer server) {
        return ACCESS.get(server);
    }

    public Collection<Session> sessions() {
        return Collections.unmodifiableCollection(sessions.values());
    }

    public boolean hasSessions() {
        return !sessions.isEmpty();
    }

    @Nullable
    public Session session(UUID id) {
        return sessions.get(id);
    }

    /** A sessao em que o jogador e participante, mesmo que ele esteja offline ou fora do plano. */
    @Nullable
    public Session sessionOf(UUID player) {
        for (Session session : sessions.values()) {
            if (session.members.contains(player)) return session;
        }
        return null;
    }

    public Session open(ResourceLocation theme, BlockPos center, List<UUID> members, List<Integer> colors,
                        List<String> names, List<Integer> sides, long now) {
        Session session = new Session(UUID.randomUUID(), theme, center, members, colors, names, sides, now, Tone.CALMO,
                State.ACTIVE);
        sessions.put(session.id, session);
        setDirty();
        return session;
    }

    public void close(UUID session) {
        if (sessions.remove(session) != null) setDirty();
    }

    public void changed() {
        setDirty();
    }

    @Nullable
    public Anchor anchor(UUID player) {
        return anchors.get(player);
    }

    public void putAnchor(UUID player, Anchor anchor) {
        anchors.put(player, anchor);
        setDirty();
    }

    public void removeAnchor(UUID player) {
        if (anchors.remove(player) != null) setDirty();
    }

    /**
     * A menor celula de arena que nenhuma sessao aberta ocupa, ou {@code null} com todas tomadas.
     *
     * <p>Sempre a partir da celula 0, e nao em rodizio: o rodizio levaria as arenas cada vez mais longe,
     * para fora da borda do mundo (que o plano herda do overworld) — e fora da borda o servidor recusa o
     * clique nos fragmentos. Reusar a celula e seguro: a arena e limpa antes de ser montada, e o que
     * sobrou dela se desfaz sozinho ao ver a sessao fechada.
     */
    @Nullable
    public BlockPos allocate() {
        for (int cell = 0; cell < GRID * GRID; cell++) {
            BlockPos center = new BlockPos((cell % GRID) * CELL_SPACING, FLOOR_Y, (cell / GRID) * CELL_SPACING);
            boolean taken = sessions.values().stream().anyMatch(s -> s.center.getX() == center.getX()
                    && s.center.getZ() == center.getZ());
            if (!taken) return center;
        }
        return null;
    }

    private static IntencaoData load(CompoundTag tag, HolderLookup.Provider registries) {
        IntencaoData data = new IntencaoData();
        ListTag list = tag.getList("Sessions", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag s = list.getCompound(i);
            ResourceLocation theme = ResourceLocation.tryParse(s.getString("Theme"));
            if (!s.hasUUID("Id") || theme == null) continue;
            List<UUID> members = new ArrayList<>();
            ListTag m = s.getList("Members", Tag.TAG_INT_ARRAY);
            for (int j = 0; j < m.size(); j++) members.add(NbtUtils.loadUUID(m.get(j)));
            List<Integer> colors = new ArrayList<>();
            for (int color : s.getIntArray("Colors")) colors.add(color);
            while (colors.size() < members.size()) colors.add(0xFFFFFF);
            List<String> names = new ArrayList<>();
            ListTag n = s.getList("Names", Tag.TAG_STRING);
            for (int j = 0; j < n.size(); j++) names.add(n.getString(j));
            while (names.size() < members.size()) names.add("?");
            List<Integer> sides = new ArrayList<>();
            for (int side : s.getIntArray("Sides")) sides.add(side);
            // Sessao gravada antes dos lados: cada um no seu, como era o veredito antigo.
            while (sides.size() < members.size()) sides.add(sides.size());
            if (members.isEmpty()) continue;
            State state = s.getBoolean("Ending") ? State.ENDING : State.ACTIVE;
            Session session = new Session(s.getUUID("Id"), theme, BlockPos.of(s.getLong("Center")), members, colors,
                    names, sides, s.getLong("Started"), Tone.byId(s.getInt("Tone")), state);
            // O veredito ja tinha sido dado quando o servidor caiu: so falta devolver. As ancoras fazem
            // isso no login, sem a sessao.
            if (state == State.ENDING) continue;
            data.sessions.put(session.id, session);
        }
        ListTag a = tag.getList("Anchors", Tag.TAG_COMPOUND);
        for (int i = 0; i < a.size(); i++) {
            CompoundTag t = a.getCompound(i);
            ResourceLocation dim = ResourceLocation.tryParse(t.getString("Dim"));
            if (!t.hasUUID("Player") || !t.hasUUID("Session") || dim == null) continue;
            data.anchors.put(t.getUUID("Player"), new Anchor(t.getUUID("Session"), dim,
                    new Vec3(t.getDouble("X"), t.getDouble("Y"), t.getDouble("Z")), t.getFloat("Yaw"), t.getFloat("Pitch"),
                    t.hasUUID("Echo") ? t.getUUID("Echo") : null));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Session session : sessions.values()) {
            CompoundTag s = new CompoundTag();
            s.putUUID("Id", session.id);
            s.putString("Theme", session.theme.toString());
            s.putLong("Center", session.center.asLong());
            ListTag members = new ListTag();
            for (UUID member : session.members) members.add(NbtUtils.createUUID(member));
            s.put("Members", members);
            ListTag names = new ListTag();
            for (String name : session.names) names.add(StringTag.valueOf(name));
            s.put("Names", names);
            s.put("Colors", new IntArrayTag(session.colors.stream().mapToInt(Integer::intValue).toArray()));
            s.put("Sides", new IntArrayTag(session.sides.stream().mapToInt(Integer::intValue).toArray()));
            s.putLong("Started", session.startedAt);
            s.putInt("Tone", session.tone.ordinal());
            s.putBoolean("Ending", session.state == State.ENDING);
            list.add(s);
        }
        tag.put("Sessions", list);
        ListTag a = new ListTag();
        anchors.forEach((player, anchor) -> {
            CompoundTag t = new CompoundTag();
            t.putUUID("Player", player);
            t.putUUID("Session", anchor.session());
            t.putString("Dim", anchor.dimension().toString());
            t.putDouble("X", anchor.pos().x);
            t.putDouble("Y", anchor.pos().y);
            t.putDouble("Z", anchor.pos().z);
            t.putFloat("Yaw", anchor.yaw());
            t.putFloat("Pitch", anchor.pitch());
            if (anchor.echo() != null) t.putUUID("Echo", anchor.echo());
            a.add(t);
        });
        tag.put("Anchors", a);
        return tag;
    }
}
