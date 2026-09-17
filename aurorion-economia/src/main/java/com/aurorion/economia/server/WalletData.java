package com.aurorion.economia.server;

import com.aurorion.core.data.PlayerMapNbt;
import com.aurorion.core.data.SavedDataAccess;
import com.aurorion.economia.AurorionEconomia;
import com.aurorion.economia.money.Money;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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
    private static final String KEY_STARTER_GRANTS = "StarterGrants";
    private static final String KEY_HOUSES = "HouseTreasuries";
    private static final String KEY_HOUSE_ID = "House";
    private static final String KEY_VAULT_LEVEL = "VaultLevel";

    private static final SavedDataAccess<WalletData> ACCESS =
            new SavedDataAccess<>(FILE_ID, WalletData::new, WalletData::load);

    private final Map<UUID, Long> balances = new HashMap<>();
    /** IDs de personagem, nao UUIDs de conta: cada nova historia recebe a bolsa uma vez. */
    private final Set<UUID> starterGrants = new HashSet<>();
    private final Map<ResourceLocation, HouseState> houses = new HashMap<>();

    record HouseState(long balance, int vaultLevel) { }

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
        PlayerMapNbt.readSet(tag, KEY_STARTER_GRANTS, data.starterGrants);
        ListTag houses = tag.getList(KEY_HOUSES, Tag.TAG_COMPOUND);
        for (int i = 0; i < houses.size(); i++) {
            CompoundTag entry = houses.getCompound(i);
            ResourceLocation id = ResourceLocation.tryParse(entry.getString(KEY_HOUSE_ID));
            if (id == null) continue;
            long balance = Math.min(Math.max(entry.getLong(KEY_BALANCE), 0L), Money.MAX);
            int vaultLevel = Math.max(0, Math.min(entry.getInt(KEY_VAULT_LEVEL), HouseTreasury.MAX_LEVEL));
            if (balance > 0 || vaultLevel > 0) data.houses.put(id, new HouseState(balance, vaultLevel));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put(KEY_ENTRIES, PlayerMapNbt.write(balances, (entry, balance) -> entry.putLong(KEY_BALANCE, balance)));
        tag.put(KEY_STARTER_GRANTS, PlayerMapNbt.writeSet(starterGrants));
        ListTag houses = new ListTag();
        this.houses.forEach((id, state) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString(KEY_HOUSE_ID, id.toString());
            entry.putLong(KEY_BALANCE, state.balance());
            entry.putInt(KEY_VAULT_LEVEL, state.vaultLevel());
            houses.add(entry);
        });
        tag.put(KEY_HOUSES, houses);
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

    /**
     * Credita a bolsa inicial uma unica vez para o ID imutavel do personagem.
     *
     * <p>O saldo ainda e localizado pela conta porque todos os consumidores atuais usam a conta;
     * o registro de concessao usa o personagem para que uma morte definitiva possa gerar uma nova
     * bolsa sem permitir repetir a bolsa do mesmo personagem apos reconectar.</p>
     */
    public boolean grantStarter(UUID characterId, UUID account, long fragments) {
        if (!starterGrants.add(characterId)) return false;

        long initial = Math.min(Math.max(fragments, 0L), Money.MAX);
        setBalance(account, initial);
        // Registrar a concessao separadamente torna repeticoes do evento inofensivas.
        setDirty();
        return true;
    }

    public Map<UUID, Long> playerBalances() {
        return Map.copyOf(balances);
    }

    long houseBalance(ResourceLocation house) {
        HouseState state = houses.get(house);
        return state == null ? 0L : state.balance();
    }

    int houseVaultLevel(ResourceLocation house) {
        HouseState state = houses.get(house);
        return state == null ? 0 : state.vaultLevel();
    }

    boolean setHouseBalance(ResourceLocation house, long balance) {
        HouseState before = houses.getOrDefault(house, new HouseState(0L, 0));
        long safe = Math.min(Math.max(balance, 0L), Money.MAX);
        if (before.balance() == safe) return false;
        putHouse(house, new HouseState(safe, before.vaultLevel()));
        return true;
    }

    boolean setHouseVaultLevel(ResourceLocation house, int level) {
        HouseState before = houses.getOrDefault(house, new HouseState(0L, 0));
        int safe = Math.max(0, Math.min(level, HouseTreasury.MAX_LEVEL));
        if (before.vaultLevel() == safe) return false;
        if (before.balance() > HouseTreasury.capacityForLevel(safe)) return false;
        putHouse(house, new HouseState(before.balance(), safe));
        return true;
    }

    private void putHouse(ResourceLocation house, HouseState state) {
        if (state.balance() == 0L && state.vaultLevel() == 0) houses.remove(house);
        else houses.put(house, state);
        setDirty();
    }
}
