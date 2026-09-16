package com.aurorion.profissoes.data;

import com.aurorion.core.data.SavedDataAccess;
import com.aurorion.profissoes.AurorionProfissoes;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

public final class ProfessionData extends SavedData {
    private static final SavedDataAccess<ProfessionData> ACCESS = new SavedDataAccess<>(
            "aurorion_profissoes", ProfessionData::new, ProfessionData::load);
    private final Map<UUID, Profession> professions = new HashMap<>();
    private CompoundTag unreadable;
    public static ProfessionData get(MinecraftServer server) { return ACCESS.get(server); }
    public Profession of(UUID account) { return professions.getOrDefault(account, Profession.NONE); }
    public void assign(UUID account, Profession profession) {
        if (unreadable != null) throw new IllegalStateException("Dados de profissões ilegíveis; corrija o save e reinicie.");
        if (profession == Profession.NONE) professions.remove(account); else professions.put(account, profession);
        setDirty();
    }
    public static ProfessionData load(CompoundTag tag, HolderLookup.Provider registries) {
        var data = new ProfessionData();
        try {
            if (tag.getInt("Version") != 1 || !tag.contains("Professions", Tag.TAG_LIST))
                throw new IllegalArgumentException("Formato de profissões desconhecido");
            var list = (ListTag)tag.get("Professions");
            if (!list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND)
                throw new IllegalArgumentException("Lista de profissões inválida");
            for (int i = 0; i < list.size(); i++) {
                var entry = list.getCompound(i);
                if (data.professions.putIfAbsent(entry.getUUID("Account"), Profession.parse(entry.getString("Profession"))) != null)
                    throw new IllegalArgumentException("Profissão duplicada");
            }
        } catch (RuntimeException error) {
            data.professions.clear(); data.unreadable = tag.copy();
            AurorionProfissoes.LOGGER.error("Profissoes: save preservado; edicao bloqueada.", error);
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        if (unreadable != null) return unreadable.copy();
        tag.putInt("Version", 1);
        var entries = new ListTag();
        professions.forEach((id, profession) -> {
            var entry = new CompoundTag(); entry.putUUID("Account", id); entry.putString("Profession", profession.id()); entries.add(entry);
        });
        tag.put("Professions", entries);
        return tag;
    }
}
