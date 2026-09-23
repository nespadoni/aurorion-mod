package com.aurorion.profissoes.npc;

import com.aurorion.core.data.SavedDataAccess;
import com.aurorion.profissoes.npc.NpcDefinition.Restock;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import java.time.LocalDate;
import java.util.*;

/**
 * Quanto cada oferta limitada ja vendeu no periodo atual.
 *
 * <p>Guarda o <b>periodo</b> junto da contagem em vez de zerar tudo por agendamento: a reposicao
 * acontece sozinha na primeira consulta de um periodo novo. Nao ha tarefa diaria para esquecer de
 * rodar, e um servidor desligado na virada do dia repoe igual ao subir.
 *
 * <p>O estoque e do NPC configurado (o {@code id} do JSON), nao da entidade: dois corpos do mesmo
 * mercador vendem do mesmo estoque.
 */
public final class NpcStockData extends SavedData {
    private static final SavedDataAccess<NpcStockData> ACCESS = new SavedDataAccess<>(
            "aurorion_profissoes_npc_estoque", NpcStockData::new, NpcStockData::load);
    private record Entry(long period, int sold) {}
    private final Map<String, Entry> entries = new HashMap<>();

    public static NpcStockData get(MinecraftServer server) { return ACCESS.get(server); }

    /**
     * O periodo de uma regra de reposicao.
     *
     * @param boot marca desta subida do servidor; muda a cada reinicio
     */
    public static long period(Restock restock, long boot, LocalDate today) {
        return switch (restock) {
            case RESTART -> boot;
            case DAILY -> today.toEpochDay();
            case NEVER -> 0L;
        };
    }

    public static String key(String npcId, String tradeKey) { return npcId + "/" + tradeKey; }

    public int sold(String key, long period) {
        var entry = entries.get(key);
        return entry == null || entry.period != period ? 0 : entry.sold;
    }

    public void add(String key, long period, int amount) {
        entries.put(key, new Entry(period, sold(key, period) + amount));
        setDirty();
    }

    /** Repoe todas as ofertas de um NPC. */
    public int reset(String npcId) {
        int before = entries.size();
        entries.keySet().removeIf(key -> key.startsWith(npcId + "/"));
        if (entries.size() != before) setDirty();
        return before - entries.size();
    }

    public static NpcStockData load(CompoundTag tag, HolderLookup.Provider registries) {
        var data = new NpcStockData();
        var list = tag.getList("Stock", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            var entry = list.getCompound(i);
            if (!entry.getString("Key").isEmpty())
                data.entries.put(entry.getString("Key"), new Entry(entry.getLong("Period"), Math.max(0, entry.getInt("Sold"))));
        }
        return data;
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("Version", 1);
        var list = new ListTag();
        entries.forEach((key, entry) -> {
            var out = new CompoundTag();
            out.putString("Key", key); out.putLong("Period", entry.period); out.putInt("Sold", entry.sold);
            list.add(out);
        });
        tag.put("Stock", list);
        return tag;
    }
}
