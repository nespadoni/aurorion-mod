package com.aurorion.portais.pass;

import com.aurorion.core.data.PlayerMapNbt;
import com.aurorion.core.data.SavedDataAccess;
import com.aurorion.portais.AurorionPortais;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Passes por UUID, persistidos no {@code data/} do overworld — valem para o servidor inteiro, nao
 * por dimensao. Gravar por UUID (e nao na entidade do jogador) e o que deixa dar e tirar passe de
 * quem esta offline, que e o que faz disso uma ferramenta de moderacao de verdade.
 *
 * <p>A estrutura e um mapa de listas curtas em vez de um mapa aninhado: a esmagadora maioria dos
 * jogadores tem zero passe, e quem tem tem um ou dois. Um {@code HashMap} interno por jogador
 * custaria mais memoria que a busca linear que ele evitaria.
 */
public class PassData extends SavedData {
    private static final String FILE_ID = AurorionPortais.MOD_ID + "_passes";
    private static final String KEY_ENTRIES = "Entries";
    private static final String KEY_PASSES = "Passes";

    /**
     * O acesso guarda a instancia e a fabrica. Importa aqui em particular: esta consulta acontece
     * <b>a cada tick</b> enquanto alguem esta parado dentro de um portal trancado, e montar um
     * {@code Factory} por chamada seria alocacao por tick por jogador (SDD §2).
     */
    private static final SavedDataAccess<PassData> ACCESS =
            new SavedDataAccess<>(FILE_ID, PassData::new, PassData::load);

    private final Map<UUID, List<TransitPass>> byPlayer = new HashMap<>();

    public static PassData get(MinecraftServer server) {
        return ACCESS.get(server);
    }


    private static PassData load(CompoundTag tag, HolderLookup.Provider registries) {
        PassData data = new PassData();

        PlayerMapNbt.read(tag, KEY_ENTRIES, data.byPlayer, entry -> {
            ListTag passes = entry.getList(KEY_PASSES, Tag.TAG_COMPOUND);
            List<TransitPass> parsed = new ArrayList<>(passes.size());

            for (int i = 0; i < passes.size(); i++) {
                TransitPass pass = TransitPass.load(passes.getCompound(i));
                if (pass != null) {
                    parsed.add(pass);
                }
            }
            // null descarta a entrada: jogador sem nenhum passe legivel nao volta para o mapa.
            return parsed.isEmpty() ? null : parsed;
        });
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put(KEY_ENTRIES, PlayerMapNbt.write(byPlayer, (entry, passes) -> {
            ListTag saved = new ListTag();
            for (TransitPass pass : passes) {
                saved.add(pass.save());
            }
            entry.put(KEY_PASSES, saved);
        }));
        return tag;
    }

    /** Substitui o passe que o jogador ja tivesse para a mesma dimensao — nao acumula. */
    public void grant(UUID player, TransitPass pass) {
        List<TransitPass> passes = byPlayer.computeIfAbsent(player, key -> new ArrayList<>(1));
        passes.removeIf(existing -> existing.dimension() == pass.dimension());
        passes.add(pass);
        setDirty();
    }

    @Nullable
    public TransitPass find(UUID player, ResourceKey<Level> dimension, long nowMillis) {
        List<TransitPass> passes = byPlayer.get(player);
        if (passes == null) return null;

        for (int i = 0; i < passes.size(); i++) {
            TransitPass pass = passes.get(i);
            if (pass.dimension() == dimension && pass.isValidAt(nowMillis)) {
                return pass;
            }
        }
        return null;
    }

    public boolean has(UUID player, ResourceKey<Level> dimension, long nowMillis) {
        return find(player, dimension, nowMillis) != null;
    }

    /**
     * Existe algum passe valido, para qualquer dimensao?
     *
     * <p>Usado pela checagem barata de entrada de portal: com 90 jogadores, quase todos respondem
     * {@code null} no primeiro {@code get} do mapa e nao percorrem lista nenhuma.
     */
    public boolean hasAny(UUID player, long nowMillis) {
        List<TransitPass> passes = byPlayer.get(player);
        if (passes == null) return false;

        for (int i = 0; i < passes.size(); i++) {
            if (passes.get(i).isValidAt(nowMillis)) {
                return true;
            }
        }
        return false;
    }

    /** Gasta um uso do passe dessa dimensao, removendo-o quando era o ultimo. */
    public void consume(UUID player, ResourceKey<Level> dimension, long nowMillis) {
        List<TransitPass> passes = byPlayer.get(player);
        if (passes == null) return;

        for (int i = 0; i < passes.size(); i++) {
            TransitPass pass = passes.get(i);
            if (pass.dimension() != dimension || !pass.isValidAt(nowMillis)) continue;

            TransitPass remaining = pass.consumed();
            if (remaining == null) {
                passes.remove(i);
                if (passes.isEmpty()) {
                    byPlayer.remove(player);
                }
            } else {
                passes.set(i, remaining);
            }
            setDirty();
            return;
        }
    }

    /** Passes validos agora, para exibicao. Copia — nunca entregue a lista interna. */
    public List<TransitPass> list(UUID player, long nowMillis) {
        List<TransitPass> passes = byPlayer.get(player);
        if (passes == null) return List.of();

        List<TransitPass> valid = new ArrayList<>(passes.size());
        for (TransitPass pass : passes) {
            if (pass.isValidAt(nowMillis)) {
                valid.add(pass);
            }
        }
        return valid;
    }

    /** @return quantos passes foram removidos. */
    public int revokeAll(UUID player) {
        List<TransitPass> removed = byPlayer.remove(player);
        if (removed == null || removed.isEmpty()) return 0;

        setDirty();
        return removed.size();
    }

    /** @return true se havia mesmo um passe para essa dimensao. */
    public boolean revoke(UUID player, ResourceKey<Level> dimension) {
        List<TransitPass> passes = byPlayer.get(player);
        if (passes == null) return false;

        if (!passes.removeIf(pass -> pass.dimension() == dimension)) return false;
        if (passes.isEmpty()) {
            byPlayer.remove(player);
        }
        setDirty();
        return true;
    }
}
