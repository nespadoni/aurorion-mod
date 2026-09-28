package com.aurorion.ethereal.compat;

import com.aurorion.ethereal.AurorionEthereal;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Integração opcional com o cofre do aurorion-economia. Toda regra de dinheiro mora lá; aqui só se
 * pergunta e se pede, por reflexão, para o Ethereal continuar iniciando sem a economia instalada.
 */
public final class HouseEconomyBridge {
    /** Espelho dos {@code HouseTreasury.ERR_*}. */
    public static final long ERR_INVALID = -1L;
    public static final long ERR_WALLET_LOW = -2L;
    public static final long ERR_VAULT_FULL = -3L;
    public static final long ERR_VAULT_LOW = -4L;
    public static final long ERR_WALLET_FULL = -5L;
    public static final long ERR_UNAVAILABLE = -100L;

    /** Uma melhoria do cofre: capacidade que ela dá e preço; preço negativo = compra desativada. */
    public record Tier(long capacity, long price) { }

    public record Snapshot(boolean available, long balance, long capacity, int vaultLevel, long wallet,
                           long salary, int salaryDays, long nextSalaryMillis, List<Tier> tiers) {
        public static final Snapshot UNAVAILABLE = new Snapshot(false, 0L, 0L, 0, 0L, 0L, 0, 0L, List.of());
    }

    private static final Methods METHODS = resolve();

    private HouseEconomyBridge() { }

    public static boolean available() {
        return METHODS != null;
    }

    public static Snapshot snapshot(MinecraftServer server, ResourceLocation house, UUID viewer) {
        if (METHODS == null) return Snapshot.UNAVAILABLE;
        try {
            int maxLevel = METHODS.maxLevel;
            List<Tier> tiers = new ArrayList<>(maxLevel);
            for (int level = 1; level <= maxLevel; level++) {
                tiers.add(new Tier((long) METHODS.capacityForLevel.invoke(null, level),
                        (long) METHODS.upgradePrice.invoke(null, false, level)));
            }
            return new Snapshot(true,
                    (long) METHODS.balance.invoke(null, server, house),
                    (long) METHODS.capacity.invoke(null, server, house),
                    (int) METHODS.vaultLevel.invoke(null, server, house),
                    (long) METHODS.wallet.invoke(null, server, viewer),
                    (long) METHODS.salary.invoke(null, server, house),
                    (int) METHODS.salaryDays.invoke(null, server, house),
                    (long) METHODS.nextSalary.invoke(null, server, house),
                    List.copyOf(tiers));
        } catch (ReflectiveOperationException | ClassCastException exception) {
            AurorionEthereal.LOGGER.error("Falha ao consultar o cofre da Casa {}.", house, exception);
            return Snapshot.UNAVAILABLE;
        }
    }

    /** @return quantia depositada, ou um {@code ERR_*} */
    public static long deposit(MinecraftServer server, UUID player, ResourceLocation house, long amount) {
        return move(METHODS == null ? null : METHODS.depositFromWallet, server, player, house, amount);
    }

    /** @return quantia sacada, ou um {@code ERR_*} */
    public static long withdraw(MinecraftServer server, UUID player, ResourceLocation house, long amount) {
        return move(METHODS == null ? null : METHODS.withdrawToWallet, server, player, house, amount);
    }

    /** O preço citado precisa bater com o atual: config mudada com a tela aberta não cobra outro valor. */
    public static boolean buyVaultUpgrade(MinecraftServer server, ResourceLocation house, int nextLevel, long quoted) {
        if (METHODS == null) return false;
        try {
            return (boolean) METHODS.buyUpgrade.invoke(null, server, house, false, nextLevel, quoted);
        } catch (ReflectiveOperationException exception) {
            AurorionEthereal.LOGGER.error("Falha ao comprar melhoria do cofre da Casa {}.", house, exception);
            return false;
        }
    }

    private static long move(Method method, MinecraftServer server, UUID player, ResourceLocation house, long amount) {
        if (method == null) return ERR_UNAVAILABLE;
        try {
            return (long) method.invoke(null, server, player, house, amount);
        } catch (ReflectiveOperationException exception) {
            AurorionEthereal.LOGGER.error("Falha ao movimentar o cofre da Casa {}.", house, exception);
            return ERR_UNAVAILABLE;
        }
    }

    private static Methods resolve() {
        try {
            Class<?> treasury = Class.forName("com.aurorion.economia.server.HouseTreasury");
            Class<?> wallet = Class.forName("com.aurorion.economia.server.Wallet");
            Class<?> server = MinecraftServer.class;
            Class<?> id = ResourceLocation.class;
            return new Methods(
                    treasury.getField("MAX_LEVEL").getInt(null),
                    treasury.getMethod("balance", server, id),
                    treasury.getMethod("capacity", server, id),
                    treasury.getMethod("vaultLevel", server, id),
                    treasury.getMethod("capacityForLevel", int.class),
                    treasury.getMethod("upgradePrice", boolean.class, int.class),
                    treasury.getMethod("buyUpgrade", server, id, boolean.class, int.class, long.class),
                    treasury.getMethod("depositFromWallet", server, UUID.class, id, long.class),
                    treasury.getMethod("withdrawToWallet", server, UUID.class, id, long.class),
                    treasury.getMethod("salary", server, id),
                    treasury.getMethod("salaryDays", server, id),
                    treasury.getMethod("nextSalaryInMillis", server, id),
                    wallet.getMethod("balance", server, UUID.class));
        } catch (ClassNotFoundException exception) {
            return null;
        } catch (ReflectiveOperationException exception) {
            AurorionEthereal.LOGGER.error("aurorion-economia foi encontrado, mas sua API de cofres e incompatível.", exception);
            return null;
        }
    }

    private record Methods(int maxLevel, Method balance, Method capacity, Method vaultLevel,
                           Method capacityForLevel, Method upgradePrice, Method buyUpgrade,
                           Method depositFromWallet, Method withdrawToWallet,
                           Method salary, Method salaryDays, Method nextSalary, Method wallet) { }
}
