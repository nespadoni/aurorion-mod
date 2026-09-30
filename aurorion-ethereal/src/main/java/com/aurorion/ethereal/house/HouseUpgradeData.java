package com.aurorion.ethereal.house;

import com.aurorion.core.data.SavedDataAccess;
import com.aurorion.ethereal.AurorionEthereal;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Estado global dos upgrades de cada Casa; nenhum dado pertence a um jogador ou mural isolado. */
public final class HouseUpgradeData extends SavedData {
    private static final String FILE_ID = AurorionEthereal.MOD_ID + "_house_upgrades";
    private static final SavedDataAccess<HouseUpgradeData> ACCESS =
            new SavedDataAccess<>(FILE_ID, HouseUpgradeData::new, HouseUpgradeData::load);

    /** Quantas movimentacoes do cofre o mural lembra por Casa. E vitrine, nao extrato: o log e a auditoria. */
    public static final int MAX_MOVEMENTS = 6;

    public record State(int protectorLevel, long lastLifeGrantAt) {
        public static final State EMPTY = new State(0, 0L);
    }

    /** Uma linha do cofre: quem (nome do personagem), o que ({@link Kind}), quanto e quando. */
    public record Movement(long at, String actor, Kind kind, long amount) { }

    public enum Kind { DEPOSIT, WITHDRAW, UPGRADE;
        static Kind byOrdinal(int ordinal) {
            Kind[] values = values();
            return values[Math.max(0, Math.min(ordinal, values.length - 1))];
        }
    }

    private final Map<ResourceLocation, State> states = new HashMap<>();
    private final Map<ResourceLocation, Deque<Movement>> movements = new HashMap<>();

    public static HouseUpgradeData get(MinecraftServer server) {
        return ACCESS.get(server);
    }

    public State state(ResourceLocation house) {
        return states.getOrDefault(house, State.EMPTY);
    }

    public int setProtectorLevel(ResourceLocation house, int level) {
        State before = state(house);
        int safe = Math.max(0, Math.min(level, 2));
        if (safe != before.protectorLevel()) {
            states.put(house, new State(safe, before.lastLifeGrantAt()));
            setDirty();
        }
        return safe;
    }

    public void recordLifeGrant(ResourceLocation house, long now) {
        State before = state(house);
        states.put(house, new State(before.protectorLevel(), Math.max(1L, now)));
        setDirty();
    }

    /** Mais recente primeiro. */
    public List<Movement> movements(ResourceLocation house) {
        Deque<Movement> list = movements.get(house);
        return list == null ? List.of() : List.copyOf(list);
    }

    public void recordMovement(ResourceLocation house, Movement movement) {
        Deque<Movement> list = movements.computeIfAbsent(house, ignored -> new ArrayDeque<>());
        list.addFirst(movement);
        while (list.size() > MAX_MOVEMENTS) list.removeLast();
        setDirty();
    }

    private static HouseUpgradeData load(CompoundTag tag, HolderLookup.Provider registries) {
        HouseUpgradeData data = new HouseUpgradeData();
        ListTag entries = tag.getList("Houses", Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            ResourceLocation house = ResourceLocation.tryParse(entry.getString("House"));
            if (house != null) {
                data.states.put(house, new State(
                        Math.max(0, Math.min(entry.getInt("ProtectorLevel"), 2)),
                        Math.max(0L, entry.getLong("LastLifeGrantAt"))));
            }
        }
        ListTag moves = tag.getList("Movements", Tag.TAG_COMPOUND);
        for (int i = 0; i < moves.size(); i++) {
            CompoundTag entry = moves.getCompound(i);
            ResourceLocation house = ResourceLocation.tryParse(entry.getString("House"));
            if (house == null) continue;
            Deque<Movement> list = data.movements.computeIfAbsent(house, ignored -> new ArrayDeque<>());
            if (list.size() < MAX_MOVEMENTS) {
                list.addLast(new Movement(entry.getLong("At"), entry.getString("Actor"),
                        Kind.byOrdinal(entry.getInt("Kind")), Math.max(0L, entry.getLong("Amount"))));
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag entries = new ListTag();
        states.forEach((house, state) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("House", house.toString());
            entry.putInt("ProtectorLevel", state.protectorLevel());
            entry.putLong("LastLifeGrantAt", state.lastLifeGrantAt());
            entries.add(entry);
        });
        tag.put("Houses", entries);

        // Lista plana, na ordem do deque (mais recente primeiro); o load respeita a mesma ordem.
        ListTag moves = new ListTag();
        movements.forEach((house, list) -> list.forEach(movement -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("House", house.toString());
            entry.putLong("At", movement.at());
            entry.putString("Actor", movement.actor());
            entry.putInt("Kind", movement.kind().ordinal());
            entry.putLong("Amount", movement.amount());
            moves.add(entry);
        }));
        tag.put("Movements", moves);
        return tag;
    }
}
