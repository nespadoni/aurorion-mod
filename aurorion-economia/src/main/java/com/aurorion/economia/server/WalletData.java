package com.aurorion.economia.server;

import com.aurorion.core.data.PlayerMapNbt;
import com.aurorion.core.data.SavedDataAccess;
import com.aurorion.economia.AurorionEconomia;
import com.aurorion.economia.money.Money;
import com.aurorion.economia.land.LandDeed;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
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
    private static final String KEY_SALARY = "Salary";
    private static final String KEY_SALARY_DAYS = "SalaryDays";
    private static final String KEY_LAST_SALARY = "LastSalaryAt";

    private static final SavedDataAccess<WalletData> ACCESS =
            new SavedDataAccess<>(FILE_ID, WalletData::new, WalletData::load);

    private final Map<UUID, Long> balances = new HashMap<>();
    /** IDs de personagem, nao UUIDs de conta: cada nova historia recebe a bolsa uma vez. */
    private final Set<UUID> starterGrants = new HashSet<>();
    private final Map<ResourceLocation, HouseState> houses = new HashMap<>();
    private final Map<UUID, LandDeed> deeds = new HashMap<>();
    private Tag unreadableDeeds;
    public static final int MAX_DEEDS = 4096;
    /**
     * Escrituras por personagem. Sem isto um corretor e um comprador combinados podiam encher o
     * cadastro global ({@link #MAX_DEEDS}) de lotes 1x1 pelo preco minimo e travar a venda de terra
     * para o servidor inteiro.
     */
    public static final int MAX_DEEDS_PER_CHARACTER = 8;

    /**
     * Tempo de jogo (em ticks de {@code minecraft:play_time}) que a conta precisa acumular depois de
     * receber a bolsa inicial para poder <b>repassar</b> esse valor: 4 horas. Gastar no NPC, na loja
     * ou num terreno e livre desde o primeiro minuto; so a transferencia para outra conta espera.
     * Conta secundaria criada so para doar a bolsa ao personagem principal teria que jogar 4 horas
     * por bolsa, e a fazenda deixa de compensar.
     */
    public static final long STARTER_LOCK_TICKS = 4L * 60 * 60 * 20;
    private static final String KEY_STARTER_LOCKS = "StarterLocks";

    /** Bolsa inicial ainda presa a conta: o valor e o {@code play_time} da conta na concessao. */
    record StarterLock(long amount, long playTicksAtGrant) { }
    private final Map<UUID, StarterLock> starterLocks = new HashMap<>();

    /** Servidor dono desta instancia; null em teste de unidade (livro-caixa vira no-op). */
    @Nullable
    private MinecraftServer server;
    /** Relogio monotono da ultima gravacao duravel ({@link #flushIfDue}). */
    private static long lastFlush;
    /** Intervalo minimo entre gravacoes duraveis: agrupa as operacoes de dinheiro em lotes. */
    private static final long FLUSH_INTERVAL_MS = 5_000L;

    /** Salario zero = desligado; {@code lastSalaryAt} e o relogio real do ultimo pagamento. */
    record HouseState(long balance, int vaultLevel, int protectorLevel,
                      long salary, int salaryDays, long lastSalaryAt) {
        static final HouseState EMPTY = new HouseState(0L, 0, 0, 0L, 0, 0L);

        boolean isEmpty() {
            return balance == 0L && vaultLevel == 0 && protectorLevel == 0 && salary == 0L;
        }

        HouseState withBalance(long value) {
            return new HouseState(value, vaultLevel, protectorLevel, salary, salaryDays, lastSalaryAt);
        }

        HouseState withLevels(int vault, int protector) {
            return new HouseState(balance, vault, protector, salary, salaryDays, lastSalaryAt);
        }

        HouseState withSalary(long amount, int days, long lastAt) {
            return new HouseState(balance, vaultLevel, protectorLevel, amount, days, lastAt);
        }
    }

    public static WalletData get(MinecraftServer server) {
        WalletData data = ACCESS.get(server);
        data.server = server;
        return data;
    }

    /**
     * Grava a carteira em disco agora, se houver mudanca e o ultimo lote tiver mais de
     * {@value #FLUSH_INTERVAL_MS} ms. Chamado a cada tick pelo {@code EconomyEvents}.
     *
     * <h2>Por que nao esperar o autosave</h2>
     *
     * <p>O autosave do vanilla roda de 5 em 5 minutos. Um crash nesse intervalo voltava a carteira
     * para o estado antigo enquanto o inventario do jogador (gravado no logout ou em outro momento)
     * podia ja conter o que foi comprado do NPC: compra de graca, repetivel derrubando o servidor.
     * Com o lote curto a janela cai para segundos e o erro passa a ser no sentido seguro: o pior
     * caso e o dinheiro saido sem o item, nunca o item sem o dinheiro.</p>
     *
     * <p>Mesma tecnica do {@code DurableSavedData} do core (arquivo temporario, {@code force},
     * troca atomica), que e package-private la; aqui so para este arquivo. Na thread do servidor,
     * como o autosave: os dois nunca escrevem o mesmo arquivo ao mesmo tempo.</p>
     */
    public static void flushIfDue(MinecraftServer server) {
        long now = Util.getMillis();
        if (now - lastFlush < FLUSH_INTERVAL_MS) return;
        WalletData data = get(server);
        if (!data.isDirty()) return;
        lastFlush = now;
        try {
            Path directory = server.getWorldPath(LevelResource.ROOT).resolve("data");
            Files.createDirectories(directory);
            Path target = directory.resolve(FILE_ID + ".dat");
            Path temporary = directory.resolve(FILE_ID + ".economia-tmp");
            CompoundTag file = new CompoundTag();
            file.put("data", data.save(new CompoundTag(), server.registryAccess()));
            file.putInt("DataVersion", SharedConstants.getCurrentVersion().getDataVersion().getVersion());
            NbtIo.writeCompressed(file, temporary);
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) { channel.force(true); }
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            data.setDirty(false);
        } catch (IOException | RuntimeException error) {
            // Fica sujo: o proximo lote ou o autosave tentam de novo.
            AurorionEconomia.LOGGER.error("Nao consegui gravar a carteira em disco agora; tento de novo no proximo lote.", error);
        }
    }

    private static WalletData load(CompoundTag tag, HolderLookup.Provider registries) {
        WalletData data = new WalletData();
        // Devolver null descarta a linha: uma entrada zerada por um save antigo nao volta como chave.
        // Saldo acima do teto (save editado a mao, versao antiga) volta ao teto: o resto do codigo
        // conta com Money.MAX para somar dois saldos sem estourar o long.
        PlayerMapNbt.read(tag, KEY_ENTRIES, data.balances, entry -> {
            long balance = entry.getLong(KEY_BALANCE);
            return balance > 0 ? Math.min(balance, Money.MAX) : null;
        });
        PlayerMapNbt.readSet(tag, KEY_STARTER_GRANTS, data.starterGrants);
        PlayerMapNbt.read(tag, KEY_STARTER_LOCKS, data.starterLocks, entry -> {
            long amount = Math.min(Math.max(entry.getLong("Amount"), 0L), Money.MAX);
            return amount > 0 ? new StarterLock(amount, Math.max(0L, entry.getLong("PlayTicks"))) : null;
        });
        ListTag houses = tag.getList(KEY_HOUSES, Tag.TAG_COMPOUND);
        for (int i = 0; i < houses.size(); i++) {
            CompoundTag entry = houses.getCompound(i);
            ResourceLocation id = ResourceLocation.tryParse(entry.getString(KEY_HOUSE_ID));
            if (id == null) continue;
            long balance = Math.min(Math.max(entry.getLong(KEY_BALANCE), 0L), Money.MAX);
            int vaultLevel = Math.max(0, Math.min(entry.getInt(KEY_VAULT_LEVEL), HouseTreasury.MAX_LEVEL));
            int protectorLevel = Math.max(0, Math.min(entry.getInt("ProtectorLevel"), 2));
            long salary = Math.min(Math.max(entry.getLong(KEY_SALARY), 0L), Money.MAX);
            int salaryDays = Math.max(0, Math.min(entry.getInt(KEY_SALARY_DAYS), HouseTreasury.MAX_SALARY_DAYS));
            if (salaryDays == 0) salary = 0L;
            HouseState state = new HouseState(balance, vaultLevel, protectorLevel,
                    salary, salaryDays, Math.max(0L, entry.getLong(KEY_LAST_SALARY)));
            if (!state.isEmpty()) data.houses.put(id, state);
        }
        try {
            if (tag.contains("LandDeeds") && !tag.contains("LandDeeds", Tag.TAG_LIST))
                throw new IllegalArgumentException("Invalid LandDeeds tag");
            ListTag deeds = tag.getList("LandDeeds", Tag.TAG_COMPOUND);
            if (deeds.size() > MAX_DEEDS) throw new IllegalArgumentException("Too many deeds");
            for (int i = 0; i < deeds.size(); i++) {
                LandDeed deed = LandDeed.load(deeds.getCompound(i));
                if (data.deeds.putIfAbsent(deed.id(), deed) != null)
                    throw new IllegalArgumentException("Duplicate deed");
            }
        } catch (RuntimeException exception) {
            data.deeds.clear();
            data.unreadableDeeds = tag.get("LandDeeds").copy();
            AurorionEconomia.LOGGER.error("Land registry is unreadable. Sales disabled; original data preserved.", exception);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put(KEY_ENTRIES, PlayerMapNbt.write(balances, (entry, balance) -> entry.putLong(KEY_BALANCE, balance)));
        tag.put(KEY_STARTER_GRANTS, PlayerMapNbt.writeSet(starterGrants));
        tag.put(KEY_STARTER_LOCKS, PlayerMapNbt.write(starterLocks, (entry, lock) -> {
            entry.putLong("Amount", lock.amount());
            entry.putLong("PlayTicks", lock.playTicksAtGrant());
        }));
        ListTag houses = new ListTag();
        this.houses.forEach((id, state) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString(KEY_HOUSE_ID, id.toString());
            entry.putLong(KEY_BALANCE, state.balance());
            entry.putInt(KEY_VAULT_LEVEL, state.vaultLevel());
            entry.putInt("ProtectorLevel", state.protectorLevel());
            if (state.salary() > 0L) {
                entry.putLong(KEY_SALARY, state.salary());
                entry.putInt(KEY_SALARY_DAYS, state.salaryDays());
                entry.putLong(KEY_LAST_SALARY, state.lastSalaryAt());
            }
            houses.add(entry);
        });
        tag.put(KEY_HOUSES, houses);
        if (unreadableDeeds != null) tag.put("LandDeeds", unreadableDeeds.copy());
        else {
            ListTag deeds = new ListTag();
            this.deeds.values().forEach(deed -> deeds.add(deed.save()));
            tag.put("LandDeeds", deeds);
        }
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
        return grantStarter(characterId, account, fragments, -1L);
    }

    /**
     * A bolsa e <b>somada</b> ao saldo, nunca o substitui: se a staff deu dinheiro antes de o
     * personagem ser nomeado, ou se o evento chegar depois de uma compra, sobrescrever apagaria
     * dinheiro (ou o devolveria, se o saldo fosse menor). O reset por morte ja zera o saldo antes.
     *
     * @param playTicks {@code play_time} atual da conta; negativo nao trava a bolsa (testes, migracao)
     */
    public boolean grantStarter(UUID characterId, UUID account, long fragments, long playTicks) {
        if (!starterGrants.add(characterId)) return false;

        long initial = Math.min(Math.max(fragments, 0L), Money.MAX);
        setBalance(account, Math.min(balance(account) + initial, Money.MAX));
        if (playTicks >= 0 && initial > 0) starterLocks.put(account, new StarterLock(initial, playTicks));
        // Registrar a concessao separadamente torna repeticoes do evento inofensivas.
        setDirty();
        return true;
    }

    /**
     * Quanto do saldo pode ser repassado a outra conta agora. A bolsa inicial fica presa ate a conta
     * jogar {@link #STARTER_LOCK_TICKS}; depois disso a trava some sozinha.
     *
     * @param playTicks {@code play_time} atual da conta; negativo (offline) conta como sem progresso
     */
    public long transferable(UUID account, long playTicks) {
        long balance = balance(account);
        StarterLock lock = starterLocks.get(account);
        if (lock == null) return balance;
        if (playTicks >= 0 && playTicks - lock.playTicksAtGrant() >= STARTER_LOCK_TICKS) {
            starterLocks.remove(account);
            setDirty();
            return balance;
        }
        return Math.max(0L, balance - lock.amount());
    }

    /** O personagem morreu de vez: a trava da bolsa antiga nao vale para a proxima historia. */
    public void clearStarterLock(UUID account) {
        if (starterLocks.remove(account) != null) setDirty();
    }

    public Map<UUID, Long> playerBalances() {
        return Map.copyOf(balances);
    }

    public java.util.Collection<LandDeed> deeds() { return java.util.List.copyOf(deeds.values()); }

    public boolean canSell(LandDeed deed) {
        return sellRefusal(deed) == null;
    }

    /** O motivo de o lote nao poder ser registrado, ou {@code null}. */
    @Nullable
    public String sellRefusal(LandDeed deed) {
        if (unreadableDeeds != null) return "O cadastro de terrenos está ilegível; a staff precisa corrigir o save.";
        if (deeds.size() >= MAX_DEEDS || deeds.containsKey(deed.id())) return "O cadastro de terrenos está cheio.";
        long owned = deeds.values().stream().filter(other -> other.ownerCharacter().equals(deed.ownerCharacter())).count();
        if (owned >= MAX_DEEDS_PER_CHARACTER)
            return "Este personagem já tem " + MAX_DEEDS_PER_CHARACTER + " terrenos registrados.";
        if (deeds.values().stream().anyMatch(deed::overlaps)) return "O lote se sobrepõe a outro terreno.";
        return null;
    }

    /**
     * Cancela uma escritura (staff). O dinheiro pago nao volta: a compra foi um ralo para o sistema,
     * e a devolucao, se for o caso, e uma decisao da staff via {@code /economia dar}.
     *
     * @return a escritura removida, ou {@code null} se nao existe ou o cadastro esta ilegivel
     */
    @Nullable
    public LandDeed revokeDeed(UUID id) {
        if (unreadableDeeds != null) return null;
        LandDeed removed = deeds.remove(id);
        if (removed != null) setDirty();
        return removed;
    }

    /** Payment is a sink: no broker commission. Title and debit persist in the same save. */
    public boolean buyLand(UUID payer, LandDeed deed) {
        if (balance(payer) < deed.paid() || !canSell(deed)) return false;
        deeds.put(deed.id(), deed);
        setBalance(payer, balance(payer) - deed.paid());
        setDirty();
        MoneyLedger.debit(server, payer, deed.paid(), "terreno:" + deed.id());
        return true;
    }

    HouseState house(ResourceLocation house) {
        return houses.getOrDefault(house, HouseState.EMPTY);
    }

    java.util.Set<ResourceLocation> houseIds() {
        return java.util.Set.copyOf(houses.keySet());
    }

    long houseBalance(ResourceLocation house) {
        return house(house).balance();
    }

    int houseVaultLevel(ResourceLocation house) {
        return house(house).vaultLevel();
    }

    boolean setHouseBalance(ResourceLocation house, long balance) {
        HouseState before = house(house);
        long safe = Math.min(Math.max(balance, 0L), Money.MAX);
        if (before.balance() == safe) return false;
        putHouse(house, before.withBalance(safe));
        // Deposito de sistema, salario, saque ou ajuste de staff: quem chama diz a origem por escopo.
        String origin = MoneyLedger.currentOrigin("casa:sistema");
        if (safe > before.balance())
            MoneyLedger.record(server, MoneyLedger.Party.system(), MoneyLedger.Party.house(house), safe - before.balance(), origin);
        else
            MoneyLedger.record(server, MoneyLedger.Party.house(house), MoneyLedger.Party.system(), before.balance() - safe, origin);
        return true;
    }

    boolean setHouseVaultLevel(ResourceLocation house, int level) {
        HouseState before = house(house);
        int safe = Math.max(0, Math.min(level, HouseTreasury.MAX_LEVEL));
        if (before.vaultLevel() == safe) return false;
        if (before.balance() > HouseTreasury.capacityForLevel(safe)) return false;
        putHouse(house, before.withLevels(safe, before.protectorLevel()));
        return true;
    }

    void setHouseSalary(ResourceLocation house, long amount, int days, long lastAt) {
        HouseState before = house(house);
        putHouse(house, amount <= 0L || days <= 0
                ? before.withSalary(0L, 0, 0L)
                : before.withSalary(Math.min(amount, Money.MAX), days, Math.max(0L, lastAt)));
    }

    /**
     * Carteira e cofre moram no mesmo SavedData: debitar um lado e creditar o outro e uma unica
     * alteracao gravada junto, sem janela em que o dinheiro exista nos dois lugares ou em nenhum.
     */
    boolean moveBetweenWalletAndHouse(java.util.UUID player, ResourceLocation house, long toHouse) {
        long wallet = balance(player);
        HouseState state = house(house);
        long walletAfter = wallet - toHouse;
        long houseAfter = state.balance() + toHouse;
        if (toHouse == 0L || walletAfter < 0L || houseAfter < 0L || walletAfter > Money.MAX || houseAfter > Money.MAX)
            return false;
        // Depositar no cofre e entregar o dinheiro aos outros membros da Casa: valem as mesmas
        // travas da transferencia (Limbo/morte/sem nome e bolsa inicial), senao o cofre virava o
        // desvio das duas. Sem servidor (teste de unidade) nao ha personagem para conferir.
        if (toHouse > 0 && server != null && (!Wallet.canSend(server, player)
                || transferable(player, Wallet.playTicks(server, player)) < toHouse))
            return false;
        setBalance(player, walletAfter);
        putHouse(house, state.withBalance(houseAfter));
        if (toHouse > 0)
            MoneyLedger.record(server, MoneyLedger.Party.account(player), MoneyLedger.Party.house(house), toHouse, "casa:deposito");
        else
            MoneyLedger.record(server, MoneyLedger.Party.house(house), MoneyLedger.Party.account(player), -toHouse, "casa:saque");
        return true;
    }

    private void putHouse(ResourceLocation house, HouseState state) {
        if (state.isEmpty()) houses.remove(house);
        else houses.put(house, state);
        setDirty();
    }

    int houseProtectorLevel(ResourceLocation house) {
        return house(house).protectorLevel();
    }

    void setHouseProtectorLevel(ResourceLocation house, int level) {
        HouseState before = house(house);
        putHouse(house, before.withLevels(before.vaultLevel(), Math.clamp(level, 0, 2)));
    }

    /** Balance and acquired level share the same SavedData and change together. */
    boolean buyUpgrade(ResourceLocation house, boolean protector, int nextLevel, long cost) {
        HouseState before = house(house);
        int level = protector ? before.protectorLevel() : before.vaultLevel();
        if (cost < 0 || cost > Money.MAX || nextLevel != level + 1
                || nextLevel > (protector ? 2 : 3) || before.balance() < cost) return false;
        putHouse(house, before.withBalance(before.balance() - cost).withLevels(
                protector ? before.vaultLevel() : nextLevel, protector ? nextLevel : before.protectorLevel()));
        MoneyLedger.record(server, MoneyLedger.Party.house(house), MoneyLedger.Party.system(), cost,
                "casa:melhoria:" + (protector ? "protetor" : "cofre") + nextLevel);
        return true;
    }
}
