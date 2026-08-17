package com.aurorion.vidas.client;

/**
 * O que o cliente sabe sobre as proprias vidas: dois inteiros, atualizados quando o servidor manda.
 *
 * <p>Sem polling, sem tick e sem pedir nada ao servidor — o HUD desenha o ultimo valor recebido.
 * {@code max = 0} e o estado "o servidor ainda nao falou nada" (servidor sem o mod, ou antes do
 * primeiro pacote), e e o que faz o HUD nao desenhar nada em vez de chutar cinco coracoes.
 */
public final class ClientLives {
    private static int lives;
    private static int max;

    private ClientLives() {
    }

    public static void accept(int newLives, int newMax) {
        lives = newLives;
        max = newMax;
    }

    /** Ao sair do servidor: entrar noutro mundo nao pode herdar o contador do anterior. */
    public static void clear() {
        lives = 0;
        max = 0;
    }

    public static boolean known() {
        return max > 0;
    }

    public static int lives() {
        return lives;
    }

    public static int max() {
        return max;
    }
}
