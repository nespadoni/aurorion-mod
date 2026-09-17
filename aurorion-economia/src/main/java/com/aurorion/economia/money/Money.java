package com.aurorion.economia.money;

/**
 * A moeda do Aurorion: o <b>Obolo</b> e o <b>Fragmento</b>, com 1 obolo = 10 fragmentos.
 *
 * <p>Tudo e guardado e movido como um {@code long} de <b>fragmentos</b>, a unidade indivisivel.
 * Nao existe ponto flutuante em lugar nenhum: dinheiro em {@code double} acumula erro de
 * arredondamento a cada transferencia, e num servidor de 80 pessoas isso vira uma moeda que nao
 * fecha a conta. O texto com virgula so aparece na borda — na hora de ler o que o jogador digitou e
 * na hora de desenhar.</p>
 *
 * <p>Como 1 obolo tem exatamente 10 fragmentos, a casa decimal <b>e</b> o fragmento: "12,3" e doze
 * obolos e tres fragmentos, nem mais nem menos. Por isso mais de um digito depois da virgula e
 * recusado em vez de arredondado — arredondar calado faria o jogador pagar um valor diferente do
 * que digitou.</p>
 */
public final class Money {
    public static final int FRAGMENTS_PER_OBOLO = 10;

    /**
     * Teto de um saldo. E metade do {@code long} de proposito: somar dois saldos validos (o que
     * toda transferencia faz antes de conferir o destino) nunca estoura, entao o codigo de
     * transferencia nao precisa de aritmetica saturada espalhada.
     */
    public static final long MAX = Long.MAX_VALUE / 2;

    private Money() {
    }

    public static long obolos(long fragments) {
        return fragments / FRAGMENTS_PER_OBOLO;
    }

    public static int fragmentos(long fragments) {
        return (int) Math.abs(fragments % FRAGMENTS_PER_OBOLO);
    }

    /** Forma curta, para tela de celular e HUD: {@code "12,3o"}. */
    public static String format(long fragments) {
        return (fragments < 0 ? "-" : "") + Math.abs(obolos(fragments)) + "," + fragmentos(fragments) + "o";
    }

    /** Forma por extenso, para chat e feedback de comando: {@code "12 obolos e 3 fragmentos"}. */
    public static String describe(long fragments) {
        long obolos = Math.abs(obolos(fragments));
        int fragmentos = fragmentos(fragments);
        String sign = fragments < 0 ? "-" : "";

        if (fragmentos == 0) return sign + obolos + (obolos == 1 ? " óbolo" : " óbolos");
        if (obolos == 0) return sign + fragmentos + (fragmentos == 1 ? " fragmento" : " fragmentos");
        return sign + obolos + (obolos == 1 ? " óbolo e " : " óbolos e ")
                + fragmentos + (fragmentos == 1 ? " fragmento" : " fragmentos");
    }

    /**
     * Le o que o jogador digitou: {@code "12"}, {@code "12,3"} ou {@code "12.3"} (ponto e virgula
     * valem o mesmo — teclado numerico manda ponto).
     *
     * @return a quantia em fragmentos, sempre positiva
     * @throws MoneyFormatException se o texto nao for uma quantia positiva com no maximo um digito
     *                              depois da virgula, ou se passar do teto
     */
    public static long parse(String input) {
        if (input == null) throw new MoneyFormatException("vazio");

        String text = input.trim().replace('.', ',');
        if (text.isEmpty()) throw new MoneyFormatException("vazio");

        String wholePart = text;
        int fragmentPart = 0;
        int comma = text.indexOf(',');

        if (comma >= 0) {
            wholePart = text.substring(0, comma);
            String decimals = text.substring(comma + 1);
            if (decimals.length() != 1) throw new MoneyFormatException(input);
            if (!isDigits(decimals)) throw new MoneyFormatException(input);
            fragmentPart = decimals.charAt(0) - '0';
            if (wholePart.isEmpty()) wholePart = "0";
        }

        if (!isDigits(wholePart)) throw new MoneyFormatException(input);

        long obolos;
        try {
            obolos = Long.parseLong(wholePart);
        } catch (NumberFormatException e) {
            throw new MoneyFormatException(input);
        }

        if (obolos > MAX / FRAGMENTS_PER_OBOLO) throw new MoneyFormatException(input);

        long fragments = obolos * FRAGMENTS_PER_OBOLO + fragmentPart;
        if (fragments <= 0 || fragments > MAX) throw new MoneyFormatException(input);
        return fragments;
    }

    private static boolean isDigits(String text) {
        if (text.isEmpty()) return false;
        for (int i = 0; i < text.length(); i++) {
            if (!Character.isDigit(text.charAt(i))) return false;
        }
        return true;
    }

    /** Recusa do parser. Nao estende {@code CommandSyntaxException} para o tipo servir fora de comando. */
    public static final class MoneyFormatException extends NumberFormatException {
        public MoneyFormatException(String input) {
            super(input);
        }
    }
}
