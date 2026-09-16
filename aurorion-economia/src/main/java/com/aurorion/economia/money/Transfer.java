package com.aurorion.economia.money;

/**
 * A decisao de uma transferencia, separada de onde o saldo mora.
 *
 * <p>E so aritmetica com os dois saldos e a quantia — nada de mundo, jogador ou disco. Esta
 * separada porque e a parte que precisa estar certa: "da para pagar?", "estoura o teto do destino?",
 * "e para si mesmo?". Com ela pura, os casos de borda viram teste em vez de virarem um bug que so
 * aparece com dinheiro de jogador em cima.</p>
 */
public final class Transfer {
    public enum Result {
        OK,
        /** Quantia zero ou negativa. */
        INVALID_AMOUNT,
        /** Quem paga nao tem tanto. */
        INSUFFICIENT,
        /** Pagar a si mesmo nao move nada e so polui o extrato. */
        SAME_ACCOUNT,
        /** O destino passaria do teto. Na pratica so acontece com dinheiro criado por staff. */
        TARGET_FULL;

        public boolean ok() {
            return this == OK;
        }
    }

    private Transfer() {
    }

    public static Result check(long fromBalance, long toBalance, long amount, boolean sameAccount) {
        if (sameAccount) return Result.SAME_ACCOUNT;
        if (amount <= 0 || amount > Money.MAX) return Result.INVALID_AMOUNT;
        if (fromBalance < amount) return Result.INSUFFICIENT;
        if (toBalance > Money.MAX - amount) return Result.TARGET_FULL;
        return Result.OK;
    }
}
