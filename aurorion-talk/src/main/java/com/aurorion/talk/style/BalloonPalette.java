package com.aurorion.talk.style;

/**
 * Cores de balao: uma grade para escolher rapido e liberdade total para quem quer a cor exata.
 *
 * <h2>O que mudou, e por que</h2>
 *
 * <p>Antes existiam duas listas fechadas — dezesseis tons pastel de balao e dezesseis tons escuros
 * de texto — e {@code isWellFormed} recusava qualquer cor fora delas. A razao era boa: ninguem pode
 * escolher balao preto com texto preto, porque no fim <b>todo mundo precisa conseguir ler a fala dos
 * outros</b>. O preco e que ficava impossivel usar a cor da sua casa, a cor do seu clã, ou qualquer
 * cor viva — as paletas eram lavadas justamente para o contraste nunca quebrar por acidente.
 *
 * <p>A proibicao virou correcao: qualquer cor e aceita, e o <b>texto</b> e empurrado para perto do
 * preto ou do branco ate passar do contraste minimo ({@link #readableText}). A garantia continua de
 * pe, sem cobrar dela a vivacidade da paleta inteira.
 *
 * <h2>A conta do contraste</h2>
 *
 * <p>Nao e "a media dos canais": o olho humano enxerga verde muito mais do que azul, e uma media
 * simples diria que {@code #0000FF} e {@code #00FF00} sao igualmente escuros. A conta e a luminancia
 * relativa da WCAG, com os canais linearizados antes de pesar. E a mesma que navegador e ferramenta
 * de acessibilidade usam, entao o resultado bate com o que qualquer verificador de contraste diria.
 *
 * <p>O limite e 3,0, e nao os 4,5 de texto corrido: a fala do balao e curta, grande e desenhada com
 * sombra. Exigir 4,5 achataria escolha demais para resolver um problema que nao existe nesse tamanho.
 */
public final class BalloonPalette {
    /** Abaixo disto o texto e reescrito. Ver o javadoc da classe para por que nao e 4,5. */
    public static final double MIN_CONTRAST = 3.0D;

    /** Largura da grade na GUI. As tres faixas abaixo tem esse tamanho cada. */
    public static final int COLUMNS = 10;

    /**
     * A grade, em tres faixas de dez: neutros, vivos e profundos.
     *
     * <p>Serve para balao e para texto — nao ha mais duas listas, porque nao ha mais duas regras. O
     * que impede a combinacao ilegivel e {@link #readableText}, nao a lista.
     */
    public static final int[] GRID = {
            // Neutros
            0xFFFFFF, 0xE4E4E7, 0xC2C2CA, 0x9A9AA5, 0x71717A,
            0x52525B, 0x3F3F46, 0x27272A, 0x18181B, 0x09090B,
            // Vivos
            0xFF3B30, 0xFF7A18, 0xFFB800, 0xFFE94A, 0x9BE80C,
            0x22D964, 0x00D6C2, 0x21B4FF, 0x3B6BFF, 0x8B5CFF,
            // Profundos
            0xC81FA8, 0xFF4FA3, 0x8C1008, 0x8C4A00, 0x6E7A00,
            0x1F6B2E, 0x006B6E, 0x00478C, 0x2B1C8C, 0x6B0A6B,
    };

    private BalloonPalette() {
    }

    // --- Leitura garantida ----------------------------------------------------------------------

    /**
     * A cor de texto mais proxima da pedida que ainda da para ler sobre {@code balloon}.
     *
     * <p>Empurra a cor escolhida para o preto (fundo claro) ou para o branco (fundo escuro) em passos
     * de 12%, preservando o matiz o quanto der. Trocar direto por preto ou branco seria mais simples
     * e jogaria fora a escolha da pessoa num caso em que ela quase acertou.
     *
     * <p>Converge sempre: vinte e quatro passos de 0,88 chegam a 4,6% do valor original, e preto sobre
     * qualquer fundo claro — ou branco sobre qualquer fundo escuro — passa com folga.
     */
    public static int readableText(int balloon, int text) {
        if (contrast(balloon, text) >= MIN_CONTRAST) return text;

        boolean towardBlack = luminance(balloon) > 0.18D;
        int result = text;

        for (int step = 0; step < 24 && contrast(balloon, result) < MIN_CONTRAST; step++) {
            result = towardBlack ? scale(result, 0.88F) : lift(result, 0.88F);
        }
        return result;
    }

    /** true quando a combinacao passa como esta — a GUI usa para avisar antes de o servidor corrigir. */
    public static boolean readable(int balloon, int text) {
        return contrast(balloon, text) >= MIN_CONTRAST;
    }

    /** O preto ou o branco, o que for mais legivel. E o palpite ao trocar a cor do balao. */
    public static int suggestedTextColor(int balloon) {
        return luminance(balloon) > 0.18D ? 0x18181B : 0xFFFFFF;
    }

    public static boolean isColor(int color) {
        return color >= 0x000000 && color <= 0xFFFFFF;
    }

    // --- Conta ----------------------------------------------------------------------------------

    /** Razao de contraste da WCAG: 1,0 e a mesma cor, 21,0 e preto contra branco. */
    public static double contrast(int first, int second) {
        double a = luminance(first);
        double b = luminance(second);
        double lighter = Math.max(a, b);
        double darker = Math.min(a, b);

        return (lighter + 0.05D) / (darker + 0.05D);
    }

    public static double luminance(int color) {
        return 0.2126D * linear(color >> 16 & 0xFF)
                + 0.7152D * linear(color >> 8 & 0xFF)
                + 0.0722D * linear(color & 0xFF);
    }

    /** Desfaz o gamma sRGB. Sem isto, o peso dos canais seria aplicado sobre o numero errado. */
    private static double linear(int channel) {
        double value = channel / 255.0D;
        return value <= 0.04045D ? value / 12.92D : Math.pow((value + 0.055D) / 1.055D, 2.4D);
    }

    private static int scale(int color, float factor) {
        return pack(Math.round((color >> 16 & 0xFF) * factor),
                Math.round((color >> 8 & 0xFF) * factor),
                Math.round((color & 0xFF) * factor));
    }

    private static int lift(int color, float factor) {
        return pack(255 - Math.round((255 - (color >> 16 & 0xFF)) * factor),
                255 - Math.round((255 - (color >> 8 & 0xFF)) * factor),
                255 - Math.round((255 - (color & 0xFF)) * factor));
    }

    private static int pack(int red, int green, int blue) {
        return Math.clamp(red, 0, 255) << 16 | Math.clamp(green, 0, 255) << 8 | Math.clamp(blue, 0, 255);
    }

    // --- Texto hexadecimal ----------------------------------------------------------------------

    /** @return a cor, ou -1 se o texto nao for um hex de seis digitos (com ou sem "#"). */
    public static int parseHex(String raw) {
        String hex = raw.startsWith("#") ? raw.substring(1) : raw;
        if (hex.length() != 6) return -1;

        try {
            return Integer.parseInt(hex, 16);
        } catch (NumberFormatException notHex) {
            return -1;
        }
    }

    public static String toHex(int color) {
        return String.format(java.util.Locale.ROOT, "#%06X", color & 0xFFFFFF);
    }
}
