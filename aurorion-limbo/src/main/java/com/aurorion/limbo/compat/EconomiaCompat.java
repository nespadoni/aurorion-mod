package com.aurorion.limbo.compat;

import com.aurorion.economia.money.Money;
import com.aurorion.economia.server.Wallet;
import net.minecraft.server.level.ServerPlayer;

/**
 * A carteira do {@code aurorion_economia}, vista do Limbo.
 *
 * <p>A economia e opcional para o Limbo: sem ela o Oraculo continua abrindo passagens, so nao vende.
 * Por isso a dependencia e {@code compileOnly} e <b>esta e a unica classe</b> que importa a economia —
 * a JVM so a carrega quando alguem chama um metodo daqui, e o chamador ({@code OracleShop}) confere
 * se o mod esta carregado antes, sem tocar nesta classe. Sem o mod instalado, nada da economia e
 * carregado.
 *
 * <p>Ao contrario das outras pontes, esta nao usa reflexao: a economia e do mesmo repositorio, entao
 * compilar contra ela nao traz binario de terceiro para o git.
 */
public final class EconomiaCompat {
    private EconomiaCompat() {
    }

    /** Obolos inteiros na unidade que a carteira guarda. */
    public static long fromObolos(int obolos) {
        return (long) obolos * Money.FRAGMENTS_PER_OBOLO;
    }

    public static long balance(ServerPlayer player) {
        return Wallet.balance(player.server, player.getUUID());
    }

    /**
     * Debita se houver saldo. Conferencia e debito no mesmo tick, entao nao divergem.
     *
     * @return {@code true} se o valor saiu da carteira
     */
    public static boolean charge(ServerPlayer player, long fragments) {
        if (balance(player) < fragments) return false;
        Wallet.add(player.server, player.getUUID(), -fragments);
        return true;
    }

    /** Devolve um debito que nao virou entrega. */
    public static void refund(ServerPlayer player, long fragments) {
        Wallet.add(player.server, player.getUUID(), fragments);
    }

    /** "12 obolos e 3 fragmentos" — o mesmo texto que o resto da economia mostra. */
    public static String describe(long fragments) {
        return Money.describe(fragments);
    }
}
