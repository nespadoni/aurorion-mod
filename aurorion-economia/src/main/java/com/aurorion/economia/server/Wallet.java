package com.aurorion.economia.server;

import com.aurorion.economia.money.Money;
import com.aurorion.economia.money.Transfer;
import net.minecraft.server.MinecraftServer;

import java.util.UUID;

/**
 * A porta de entrada do dinheiro no servidor. Todo caminho que move saldo — comando, app de banco
 * do celular, loja futura — passa por aqui, para que exista <b>um</b> lugar onde a conta e conferida.
 *
 * <p>Thread do servidor apenas, como o {@code SavedData} que ela usa.</p>
 */
public final class Wallet {
    private Wallet() {
    }

    public static long balance(MinecraftServer server, UUID player) {
        return WalletData.get(server).balance(player);
    }

    /** Define um saldo exato. Negativo vira zero. Uso de staff. */
    public static void set(MinecraftServer server, UUID player, long fragments) {
        WalletData.get(server).setBalance(player, Math.min(Math.max(fragments, 0L), Money.MAX));
    }

    /**
     * Soma (ou subtrai, com quantia negativa) sem deixar passar do teto nem ficar negativo.
     *
     * @return o saldo depois da operacao
     */
    public static long add(MinecraftServer server, UUID player, long fragments) {
        WalletData data = WalletData.get(server);
        long before = data.balance(player);
        long after = fragments > 0
                ? Math.min(before + Math.min(fragments, Money.MAX), Money.MAX)
                : Math.max(before + fragments, 0L);

        data.setBalance(player, after);
        return after;
    }

    /**
     * Move dinheiro de um personagem para outro. Nada e criado nem destruido: e a mesma quantia
     * saindo de um lado e entrando no outro, gravada de uma vez so.
     */
    public static Transfer.Result transfer(MinecraftServer server, UUID from, UUID to, long amount) {
        WalletData data = WalletData.get(server);
        long fromBalance = data.balance(from);
        long toBalance = data.balance(to);

        Transfer.Result result = Transfer.check(fromBalance, toBalance, amount, from.equals(to));
        if (!result.ok()) return result;

        data.setBalance(from, fromBalance - amount);
        data.setBalance(to, toBalance + amount);
        return result;
    }
}
