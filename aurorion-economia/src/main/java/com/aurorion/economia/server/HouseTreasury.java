package com.aurorion.economia.server;

import com.aurorion.economia.AurorionEconomia;
import com.aurorion.economia.compat.PhoneBankBridge;
import com.aurorion.economia.money.Money;
import com.aurorion.economia.config.EconomyConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/** Cofre coletivo de uma Casa de Ethereal, armazenado sempre em Fragmentos. */
public final class HouseTreasury {
    public static final int MAX_LEVEL = 3;
    public static final int MAX_SALARY_DAYS = 365;
    private static final long DAY_MILLIS = 86_400_000L;

    /*
     * Resultados de deposito/saque pela carteira. Sao numeros, e nao enum, porque o aurorion-ethereal
     * chama estes metodos por reflexao e nao compartilha tipo nenhum com este mod. Positivo = quantia
     * movida de fato.
     */
    public static final long ERR_INVALID = -1L;
    public static final long ERR_WALLET_LOW = -2L;
    public static final long ERR_VAULT_FULL = -3L;
    public static final long ERR_VAULT_LOW = -4L;
    public static final long ERR_WALLET_FULL = -5L;
    private static final long[] CAPACITIES = {
            100L * Money.FRAGMENTS_PER_OBOLO,
            250L * Money.FRAGMENTS_PER_OBOLO,
            500L * Money.FRAGMENTS_PER_OBOLO,
            1_000L * Money.FRAGMENTS_PER_OBOLO
    };

    public record Deposit(long requested, long accepted, long balance, long capacity) {
        public long overflow() { return requested - accepted; }
    }

    private HouseTreasury() { }

    public static int protectorLevel(MinecraftServer server, ResourceLocation house) {
        return WalletData.get(server).houseProtectorLevel(house);
    }

    public static void setProtectorLevel(MinecraftServer server, ResourceLocation house, int level) {
        WalletData.get(server).setHouseProtectorLevel(house, level);
    }

    public static long upgradePrice(boolean protector, int level) {
        return EconomyConfig.price(protector, level);
    }

    public static boolean buyUpgrade(MinecraftServer server, ResourceLocation house,
                                     boolean protector, int nextLevel, long quotedCost) {
        long cost = upgradePrice(protector, nextLevel);
        if (cost < 0 || quotedCost != cost) return false;
        boolean bought = WalletData.get(server).buyUpgrade(house, protector, nextLevel, cost);
        if (bought) EconomyProjectorNotifier.refresh(server);
        return bought;
    }

    public static java.util.Set<ResourceLocation> knownHouses(MinecraftServer server) {
        return WalletData.get(server).houseIds();
    }

    public static long balance(MinecraftServer server, ResourceLocation house) {
        return WalletData.get(server).houseBalance(house);
    }

    public static int vaultLevel(MinecraftServer server, ResourceLocation house) {
        return WalletData.get(server).houseVaultLevel(house);
    }

    public static long capacity(MinecraftServer server, ResourceLocation house) {
        return capacityForLevel(vaultLevel(server, house));
    }

    public static long capacityForLevel(int level) {
        return CAPACITIES[Math.max(0, Math.min(level, MAX_LEVEL))];
    }

    /**
     * Deposita ate a capacidade. O excedente e devolvido ao chamador para permanecer com o jogador,
     * como determina a regra do imposto quando o cofre esta cheio.
     */
    public static Deposit deposit(MinecraftServer server, ResourceLocation house, long amount) {
        if (amount <= 0 || amount > Money.MAX) return new Deposit(amount, 0L,
                balance(server, house), capacity(server, house));
        WalletData data = WalletData.get(server);
        long before = data.houseBalance(house);
        long capacity = capacityForLevel(data.houseVaultLevel(house));
        long accepted = Math.min(amount, Math.max(0L, capacity - before));
        if (accepted > 0) {
            data.setHouseBalance(house, before + accepted);
            EconomyProjectorNotifier.refresh(server);
        }
        return new Deposit(amount, accepted, before + accepted, capacity);
    }

    public static boolean withdraw(MinecraftServer server, ResourceLocation house, long amount) {
        if (amount <= 0 || amount > Money.MAX) return false;
        WalletData data = WalletData.get(server);
        long before = data.houseBalance(house);
        if (before < amount) return false;
        data.setHouseBalance(house, before - amount);
        EconomyProjectorNotifier.refresh(server);
        return true;
    }

    /** Operacao administrativa; o saldo e limitado pela capacidade do nivel atual. */
    public static long set(MinecraftServer server, ResourceLocation house, long amount) {
        WalletData data = WalletData.get(server);
        long safe = Math.min(Math.max(amount, 0L), capacityForLevel(data.houseVaultLevel(house)));
        if (data.setHouseBalance(house, safe)) EconomyProjectorNotifier.refresh(server);
        return safe;
    }

    /** Operacao administrativa/futura compra de upgrade. Nunca queima excedente ao reduzir nivel. */
    public static int setVaultLevel(MinecraftServer server, ResourceLocation house, int level) {
        WalletData data = WalletData.get(server);
        int safe = Math.max(0, Math.min(level, MAX_LEVEL));
        if (data.setHouseVaultLevel(house, safe)) EconomyProjectorNotifier.refresh(server);
        return data.houseVaultLevel(house);
    }

    /**
     * Tira da carteira do jogador e poe no cofre. Se o cofre nao comporta tudo, entra so o que cabe
     * e o resto nem sai da carteira.
     *
     * @return quantia depositada, ou um dos {@code ERR_*}
     */
    public static long depositFromWallet(MinecraftServer server, UUID player, ResourceLocation house, long amount) {
        if (amount <= 0 || amount > Money.MAX) return ERR_INVALID;
        WalletData data = WalletData.get(server);
        if (data.balance(player) < amount) return ERR_WALLET_LOW;
        long room = Math.max(0L, capacityForLevel(data.houseVaultLevel(house)) - data.houseBalance(house));
        long accepted = Math.min(amount, room);
        if (accepted <= 0L) return ERR_VAULT_FULL;
        if (!data.moveBetweenWalletAndHouse(player, house, accepted)) return ERR_INVALID;
        afterWalletMove(server, player);
        return accepted;
    }

    /** @return quantia sacada para a carteira, ou um dos {@code ERR_*} */
    public static long withdrawToWallet(MinecraftServer server, UUID player, ResourceLocation house, long amount) {
        if (amount <= 0 || amount > Money.MAX) return ERR_INVALID;
        WalletData data = WalletData.get(server);
        if (data.houseBalance(house) < amount) return ERR_VAULT_LOW;
        if (data.balance(player) + amount > Money.MAX) return ERR_WALLET_FULL;
        if (!data.moveBetweenWalletAndHouse(player, house, -amount)) return ERR_INVALID;
        afterWalletMove(server, player);
        return amount;
    }

    private static void afterWalletMove(MinecraftServer server, UUID player) {
        EconomyProjectorNotifier.refresh(server);
        ServerPlayer online = server.getPlayerList().getPlayer(player);
        // O saldo do app de banco so e lido quando o celular pede; empurrar agora evita o jogador
        // abrir o celular logo depois do saque e ver o valor antigo.
        if (online != null) PhoneBankBridge.sync(online);
    }

    // --- Salario da Casa -------------------------------------------------------------------------

    public static long salary(MinecraftServer server, ResourceLocation house) {
        return WalletData.get(server).house(house).salary();
    }

    public static int salaryDays(MinecraftServer server, ResourceLocation house) {
        return WalletData.get(server).house(house).salaryDays();
    }

    /** Milissegundos ate o proximo pagamento; 0 com salario desligado. */
    public static long nextSalaryInMillis(MinecraftServer server, ResourceLocation house) {
        WalletData.HouseState state = WalletData.get(server).house(house);
        if (state.salary() <= 0L) return 0L;
        return Math.max(0L, state.lastSalaryAt() + state.salaryDays() * DAY_MILLIS - System.currentTimeMillis());
    }

    /** Define o salario; o relogio comeca agora, entao o primeiro pagamento sai daqui a {@code days}. */
    public static void setSalary(MinecraftServer server, ResourceLocation house, long amount, int days) {
        int safeDays = Math.max(0, Math.min(days, MAX_SALARY_DAYS));
        WalletData.get(server).setHouseSalary(house, amount, safeDays, System.currentTimeMillis());
    }

    /**
     * Paga agora um periodo do salario e reinicia o relogio.
     *
     * @return o deposito feito (o excedente acima da capacidade nao e criado)
     */
    public static Deposit paySalaryNow(MinecraftServer server, ResourceLocation house) {
        WalletData data = WalletData.get(server);
        WalletData.HouseState state = data.house(house);
        if (state.salary() <= 0L) return new Deposit(0L, 0L, state.balance(), capacity(server, house));
        Deposit deposit = deposit(server, house, state.salary());
        data.setHouseSalary(house, state.salary(), state.salaryDays(), System.currentTimeMillis());
        return deposit;
    }

    /**
     * Paga os periodos vencidos de todas as casas. Chamado de minuto em minuto: com cinco casas o
     * custo e desprezivel e nao depende de ninguem abrir o mural.
     *
     * <p>Servidor desligado por tres semanas com salario semanal paga tres vezes na volta. O que
     * passa da capacidade do cofre nao e criado — e isso que faz a casa sacar ou comprar melhoria.</p>
     */
    public static void paySalaries(MinecraftServer server) {
        WalletData data = WalletData.get(server);
        long now = System.currentTimeMillis();
        for (ResourceLocation house : data.houseIds()) {
            WalletData.HouseState state = data.house(house);
            if (state.salary() <= 0L || state.salaryDays() <= 0) continue;
            long period = state.salaryDays() * DAY_MILLIS;
            long due = (now - state.lastSalaryAt()) / period;
            if (due <= 0) continue;

            long owed = due > Money.MAX / state.salary() ? Money.MAX : state.salary() * due;
            Deposit deposit = deposit(server, house, owed);
            data.setHouseSalary(house, state.salary(), state.salaryDays(), state.lastSalaryAt() + due * period);
            AurorionEconomia.LOGGER.info("SALARIO_CASA house={} periodos={} pago={} perdido={} saldo={}",
                    house, due, deposit.accepted(), deposit.overflow(), deposit.balance());
        }
    }
}
