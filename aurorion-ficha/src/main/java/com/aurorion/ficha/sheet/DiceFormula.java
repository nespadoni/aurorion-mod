package com.aurorion.ficha.sheet;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * A fórmula de rolagem livre com a mesma gramática do motor do site ({@code roll.ParseFormula}):
 *
 * <pre>
 * fórmula   := [R ('#'|'x')] expressão
 * expressão := parcela (('+'|'-') parcela)*
 * parcela   := [N] 'd' (F|'%') [('kh'|'kl'|'dh'|'dl') N] | inteiro
 * </pre>
 *
 * {@code R} é a repetição (notação Roll20/Avrae): {@code 2#d20+5} rola a expressão duas vezes, cada
 * uma com o seu total (dois ataques); o site devolve as repetições em {@code repeats}
 * ({@link RollResult#repeats()}). {@code x} é sinônimo só como prefixo ({@code 2x1d20+5} — e é o que
 * cabe numa palavra do Brigadier, onde {@code #} não entra); a forma canônica usa {@code #} e
 * {@code 1#} é uma rolagem só. 1 a {@link #MAX_REPEAT} vezes; o teto de dados vale para todas somadas.
 *
 * <p>Os mesmos tetos do motor — até 10 parcelas, 200 dados, faces de 2 a 1000, constante até 100 000,
 * ao menos um dado; "manter" até N, "descartar" menos que N — para o erro aparecer na mesa e no comando,
 * não como {@code invalid} do site depois de o pedido sair. A única diferença é o comprimento: o site
 * aceita 120 caracteres, aqui o teto é {@link #MAX_LENGTH} (cabe com folga nos pacotes; ninguém
 * digita mais que isso).
 *
 * <p>{@link #normalize} devolve a forma canônica como o motor a escreve ({@code d20} → {@code 1d20},
 * {@code d%} → {@code 1d100}, espaços fora, minúsculas): é ela que viaja e aparece no pedido.
 */
public final class DiceFormula {
    public static final int MAX_LENGTH = 60;
    public static final int MAX_TERMS = 10;
    public static final int MAX_DICE = 200;
    public static final int MAX_SIDES = 1000;
    public static final int MAX_CONSTANT = 100_000;
    public static final int MAX_REPEAT = 10;

    /** Os exemplos que toda mensagem de fórmula recusada mostra (o formato aceito, de relance). */
    public static final String EXAMPLES = "1d20+5, 2d6, 4d6kh3 ou 2#d20+5 (duas rolagens)";

    private DiceFormula() {
    }

    /**
     * A recusa inteira para a tela ou o chat: "Fórmula inválida: faces inválidas em "1d1" (de 2 a 1000).
     * Use NdF±bônus, como 1d20+5, 2d6, 4d6kh3 ou 2#d20+5 (duas rolagens)." {@code null} se a fórmula vale.
     */
    @Nullable
    public static String explain(@Nullable String expr) {
        String problem = problem(expr);
        return problem == null ? null : "Fórmula inválida: " + problem + ". Use NdF±bônus, como " + EXAMPLES + ".";
    }

    /** Quantas vezes a fórmula rola ({@code 2#d20} → 2); 1 sem repetição ou se for inválida. */
    public static int repeat(@Nullable String expr) {
        Parsed parsed = parse(expr);
        return parsed.problem == null ? parsed.repeat : 1;
    }

    /** A forma canônica, ou {@code null} se o motor recusaria. */
    @Nullable
    public static String normalize(@Nullable String expr) {
        Parsed parsed = parse(expr);
        return parsed.problem == null ? parsed.canonical : null;
    }

    /** {@code null} se a fórmula vale; senão, o que dizer a quem digitou (sem ponto final). */
    @Nullable
    public static String problem(@Nullable String expr) {
        return parse(expr).problem;
    }

    /**
     * Um d20 só, com ou sem constante ({@code 1d20}, {@code 1d20+3}), sem repetição: a fórmula em que
     * uma CD se lê como num teste. Recebe a forma canônica (ou qualquer texto; inválido dá {@code false}).
     */
    public static boolean singleD20(@Nullable String expr) {
        Parsed parsed = parse(expr);
        return parsed.problem == null && parsed.dice == 1 && parsed.onlyD20 && parsed.repeat == 1;
    }

    private record Parsed(@Nullable String canonical, @Nullable String problem, int dice, boolean onlyD20, int repeat) {
        static Parsed bad(String problem) {
            return new Parsed(null, problem, 0, false, 1);
        }
    }

    private static Parsed parse(@Nullable String expr) {
        String s = expr == null ? "" : expr.replace(" ", "").strip().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) return Parsed.bad("escreva uma fórmula, como 2d6+3");
        if (s.length() > MAX_LENGTH) return Parsed.bad("fórmula longa demais (até " + MAX_LENGTH + " caracteres)");
        // Repetição "N#" (ou "Nx") no começo, como o motor: o resto é a expressão de sempre.
        int repeat = 1;
        int mark = firstMark(s);
        if (mark >= 0) {
            String head = s.substring(0, mark);
            int n = number(head, MAX_CONSTANT);
            if (n < 0) return Parsed.bad("a repetição vem antes da fórmula, como 2#d20+5");
            if (n < 1 || n > MAX_REPEAT) return Parsed.bad("repita de 1 a " + MAX_REPEAT + " vezes (como 2#d20+5)");
            repeat = n;
            s = s.substring(mark + 1);
            if (firstMark(s) >= 0) return Parsed.bad("uma repetição só, no começo (como 2#d20+5)");
            if (s.isEmpty()) return Parsed.bad("falta a fórmula depois de \"" + head + "#\" (como 2#d20+5)");
        }
        StringBuilder out = new StringBuilder();
        if (repeat > 1) out.append(repeat).append('#');
        int prefix = out.length();
        int terms = 0, dice = 0;
        boolean onlyD20 = true;
        int i = 0;
        while (i < s.length()) {
            int sign = 1;
            char c = s.charAt(i);
            if (c == '+' || c == '-') {
                sign = c == '-' ? -1 : 1;
                i++;
            }
            int j = i;
            while (j < s.length() && s.charAt(j) != '+' && s.charAt(j) != '-') j++;
            String raw = s.substring(i, j);
            if (raw.isEmpty()) return Parsed.bad("sinal sem número ou dado depois");
            if (++terms > MAX_TERMS) return Parsed.bad("mais de " + MAX_TERMS + " parcelas");
            if (sign < 0) out.append('-');
            else if (out.length() > prefix) out.append('+');

            int d = raw.indexOf('d');
            if (d < 0) {
                int n = number(raw, MAX_CONSTANT);
                if (n < 0) return Parsed.bad("\"" + raw + "\" não é número nem dado (use NdF, como 2d6)");
                out.append(n);
            } else {
                int count = 1;
                if (d > 0) {
                    count = number(raw.substring(0, d), MAX_DICE);
                    if (count < 1) return Parsed.bad("quantidade de dados inválida em \"" + raw + "\"");
                }
                String rest = raw.substring(d + 1);
                String op = "";
                int opN = 0;
                for (String candidate : new String[]{"kh", "kl", "dh", "dl"}) {
                    int k = rest.indexOf(candidate);
                    if (k >= 0) {
                        opN = number(rest.substring(k + 2), MAX_DICE);
                        if (opN < 1) return Parsed.bad("\"" + rest.substring(k) + "\": depois de " + candidate + " vem quantos dados (como 2d20" + candidate + "1)");
                        op = candidate;
                        rest = rest.substring(0, k);
                        break;
                    }
                }
                int sides = "%".equals(rest) ? 100 : number(rest, MAX_SIDES);
                if (sides < 2) return Parsed.bad("faces inválidas em \"" + raw + "\" (de 2 a " + MAX_SIDES + ")");
                if (("dh".equals(op) || "dl".equals(op)) && opN >= count) {
                    return Parsed.bad("descartar " + opN + " de " + count + " dados não deixa nenhum");
                }
                if (("kh".equals(op) || "kl".equals(op)) && opN > count) {
                    return Parsed.bad("manter " + opN + " de " + count + " dados");
                }
                dice += count;
                if (dice * repeat > MAX_DICE) {
                    return Parsed.bad("mais de " + MAX_DICE + " dados" + (repeat > 1 ? " somando as " + repeat + " rolagens" : ""));
                }
                if (sides != 20 || !op.isEmpty()) onlyD20 = false;
                out.append(count).append('d').append(sides);
                if (!op.isEmpty()) out.append(op).append(opN);
            }
            i = j;
        }
        if (dice == 0) return Parsed.bad("a fórmula precisa de ao menos um dado (como 1d20)");
        return new Parsed(out.toString(), null, dice, onlyD20, repeat);
    }

    /** Onde está a marca de repetição ({@code #} ou {@code x}); {@code -1} sem nenhuma. */
    private static int firstMark(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '#' || c == 'x') return i;
        }
        return -1;
    }

    /** Só dígitos (sem sinal), até {@code max}; {@code -1} se não for isso. */
    private static int number(String text, int max) {
        if (text.isEmpty() || text.length() > 7) return -1;
        int n = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < '0' || c > '9') return -1;
            n = n * 10 + (c - '0');
        }
        return n > max ? -1 : n;
    }
}
