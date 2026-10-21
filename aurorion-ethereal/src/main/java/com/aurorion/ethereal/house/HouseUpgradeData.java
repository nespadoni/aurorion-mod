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

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Estado global dos upgrades de cada Casa; nenhum dado pertence a um jogador ou mural isolado. */
public final class HouseUpgradeData extends SavedData {
    private static final String FILE_ID = AurorionEthereal.MOD_ID + "_house_upgrades";
    private static final SavedDataAccess<HouseUpgradeData> ACCESS =
            new SavedDataAccess<>(FILE_ID, HouseUpgradeData::new, HouseUpgradeData::load);

    /** Quantas movimentacoes do cofre o mural lembra por Casa. E vitrine, nao extrato: o log e a auditoria. */
    public static final int MAX_MOVEMENTS = 6;
    /**
     * Quantas linhas de deposito o historico guarda. Menos que as que a tela mostra, de proposito: uma
     * enxurrada de depositos minusculos so gira entre estas linhas e nunca empurra um saque para fora
     * da vitrine logo depois de ele acontecer.
     */
    public static final int MAX_DEPOSIT_LINES = 2;
    /** Deposito abaixo disto (1 Obolo, em Fragmentos) nem entra no historico; continua no log. */
    public static final long HISTORY_MIN_DEPOSIT = 10L;

    public record State(int protectorLevel, long lastLifeGrantAt) {
        public static final State EMPTY = new State(0, 0L);
    }

    /**
     * Quanto um membro ja tirou do cofre da propria Casa num dia ({@code day} = dia do calendario do
     * servidor). Gravado em disco para que sair e voltar, ou reiniciar o servidor, nao zere o limite.
     */
    public record Usage(ResourceLocation house, long day, long amount) { }

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
    private final Map<UUID, Usage> usage = new HashMap<>();

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
        if (movement.kind() == Kind.DEPOSIT && movement.amount() < HISTORY_MIN_DEPOSIT) return;
        Deque<Movement> list = movements.computeIfAbsent(house, ignored -> new ArrayDeque<>());
        list.addFirst(movement);
        // Deposito so desloca deposito; saque e melhoria saem apenas pela idade.
        if (movement.kind() == Kind.DEPOSIT) {
            int deposits = 0;
            for (Iterator<Movement> it = list.iterator(); it.hasNext(); ) {
                if (it.next().kind() == Kind.DEPOSIT && ++deposits > MAX_DEPOSIT_LINES) it.remove();
            }
        }
        while (list.size() > MAX_MOVEMENTS) list.removeLast();
        setDirty();
    }

    /** Quanto {@code member} ja tirou hoje do cofre de {@code house}. Outro dia ou outra Casa = zero. */
    public long withdrawnToday(UUID member, ResourceLocation house, long day) {
        Usage current = usage.get(member);
        return current != null && current.day() == day && current.house().equals(house) ? current.amount() : 0L;
    }

    public void recordWithdrawal(UUID member, ResourceLocation house, long day, long amount) {
        if (amount <= 0L) return;
        long before = withdrawnToday(member, house, day);
        long total = before > Long.MAX_VALUE - amount ? Long.MAX_VALUE : before + amount;
        usage.put(member, new Usage(house, day, total));
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
        ListTag used = tag.getList("Withdrawals", Tag.TAG_COMPOUND);
        for (int i = 0; i < used.size(); i++) {
            CompoundTag entry = used.getCompound(i);
            ResourceLocation house = ResourceLocation.tryParse(entry.getString("House"));
            if (house == null || !entry.hasUUID("Player")) continue;
            data.usage.put(entry.getUUID("Player"),
                    new Usage(house, entry.getLong("Day"), Math.max(0L, entry.getLong("Amount"))));
        }
        return data;
    }

    /** Dia do calendario no fuso do servidor: o limite "por dia" vira a meia-noite de quem joga aqui. */
    public static long currentDay() {
        return LocalDate.now(ZoneId.systemDefault()).toEpochDay();
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

        // Dia que ja passou nao limita mais ninguem: nao precisa ir para o disco.
        long today = currentDay();
        ListTag used = new ListTag();
        usage.forEach((player, entry) -> {
            if (entry.day() < today) return;
            CompoundTag item = new CompoundTag();
            item.putUUID("Player", player);
            item.putString("House", entry.house().toString());
            item.putLong("Day", entry.day());
            item.putLong("Amount", entry.amount());
            used.add(item);
        });
        tag.put("Withdrawals", used);
        return tag;
    }
}
