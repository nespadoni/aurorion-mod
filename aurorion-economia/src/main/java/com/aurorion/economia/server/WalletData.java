package com.aurorion.economia.server;

import com.aurorion.core.data.PlayerMapNbt;
import com.aurorion.core.data.SavedDataAccess;
import com.aurorion.economia.AurorionEconomia;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * O saldo de cada personagem, em fragmentos, no save do mundo.
 *
 * <p>Saldo zero nao e gravado: a entrada some do mapa. Com 80 jogadores e a maioria sem dinheiro no
 * comeco, isso mantem o arquivo do tamanho de quem de fato tem saldo, e faz "conta nova" e "conta
 * zerada" serem o mesmo estado — nao existe diferenca para ninguem perguntar.</p>
 */
public class WalletData extends SavedData {
    private static final String FILE_ID = AurorionEconomia.MOD_ID + "_carteiras";
    private static final String KEY_ENTRIES = "Entries";
    private static final String KEY_BALANCE = "Fragmentos";

    private static final SavedDataAccess<WalletData> ACCESS =
            new SavedDataAccess<>(FILE_ID, WalletData::new, WalletData::load);

    private final Map<UUID, Long> balances = new HashMap<>();

    public static WalletData get(MinecraftServer server) {
        return ACCESS.get(server);
    }

    private static WalletData load(CompoundTag tag, HolderLookup.Provider registries) {
        WalletData data = new WalletData();
        // Devolver null descarta a linha: uma entrada zerada por um save antigo nao volta como chave.
        PlayerMapNbt.read(tag, KEY_ENTRIES, data.balances, entry -> {
            long balance = entry.getLong(KEY_BALANCE);
            return balance > 0 ? balance : null;
        });
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put(KEY_ENTRIES, PlayerMapNbt.write(balances, (entry, balance) -> entry.putLong(KEY_BALANCE, balance)));
        return tag;
    }

    public long balance(UUID player) {
        Long balance = balances.get(player);
        return balance == null ? 0L : balance;
    }

    /** @return true se algo mudou de fato. */
    public boolean setBalance(UUID player, long fragments) {
        Long previous = fragments <= 0 ? balances.remove(player) : balances.put(player, fragments);
        long before = previous == null ? 0L : previous;
        boolean changed = before != Math.max(fragments, 0L);

        if (changed) setDirty();
        return changed;
    }
}
