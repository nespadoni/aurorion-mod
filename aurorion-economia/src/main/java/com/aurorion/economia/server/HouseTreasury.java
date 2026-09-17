package com.aurorion.economia.server;

import com.aurorion.economia.money.Money;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/** Cofre coletivo de uma Casa de Ethereal, armazenado sempre em Fragmentos. */
public final class HouseTreasury {
    public static final int MAX_LEVEL = 3;
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
}
