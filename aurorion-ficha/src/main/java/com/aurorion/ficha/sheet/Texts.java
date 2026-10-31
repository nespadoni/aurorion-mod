package com.aurorion.ficha.sheet;

import org.jetbrains.annotations.Nullable;

import java.util.regex.Pattern;

/** Frases curtas que o chat, o HUD e as telas repetem — um lugar só para o mesmo texto. */
public final class Texts {
    /**
     * O sinal de menos tipográfico (U+2212, está na fonte bitmap do Minecraft): "+3" e "−1" com a
     * mesma largura. Só para exibição — fórmula de dado ({@code 1d20-2}) continua com o hífen ASCII.
     */
    public static final String MINUS = "−";

    /**
     * Onde o cliente põe o nome da tecla da ficha num aviso do servidor ({@code FichaClient.notice}): o
     * servidor não sabe qual tecla cada um configurou.
     */
    public static final String KEY_PLACEHOLDER = "{tecla}";
    /** O aviso de ficha antiga no login da conta principal (HUD). */
    public static final String OUTDATED_NOTICE = "Sua ficha ainda não foi atualizada no site — aperte " + KEY_PLACEHOLDER + " para ver.";

    private static final Pattern CODE_PREFIX = Pattern.compile("^[a-z_]+:\\s+");
    private static final Pattern BARE_CODE = Pattern.compile("[a-z_]*");

    private Texts() {
    }

    /** "Furtividade", "Força", "Atletismo (DES)", "2d6+3". */
    public static String testName(@Nullable String kind, @Nullable String key, @Nullable String attribute) {
        String k = key == null ? "" : key;
        String attr = attribute == null ? "" : attribute;
        return switch (kind == null ? "" : kind) {
            case "skill" -> {
                Catalog.Skill skill = Catalog.skill(k);
                String name = skill == null ? k : skill.label();
                Catalog.Attribute alt = Catalog.attribute(attr);
                yield attr.isEmpty() || (skill != null && alt != null && skill.attribute().equals(alt.id()))
                        ? name : name + " (" + Catalog.attributeAbbreviation(attr) + ")";
            }
            case "attribute" -> Catalog.attributeLabel(k);
            default -> k;
        };
    }

    /**
     * A chamada do pedido do mestre no HUD e na ficha: "Teste de Furtividade" ou, num pedido de dados
     * (o teste é a fórmula, {@link DiceFormula}), "Rolar 2d6". Nenhum nome de perícia ou atributo é
     * uma fórmula válida, então o texto basta para distinguir.
     */
    public static String askHeadline(@Nullable String test) {
        String t = test == null ? "" : test;
        return isDice(t) ? "Rolar " + t : "Teste de " + t;
    }

    /** O teste de um pedido é uma fórmula de dados (rolagem livre), não perícia nem atributo. */
    public static boolean isDice(@Nullable String test) {
        return test != null && !test.isEmpty() && DiceFormula.normalize(test) != null;
    }

    public static String modeLabel(@Nullable String mode) {
        if (mode == null) return "";
        return switch (mode) {
            case "vantagem" -> "com vantagem";
            case "desvantagem" -> "com desvantagem";
            default -> "";
        };
    }

    /** "+3", "+0", "−2" (sinal tipográfico; ver {@link #MINUS}). */
    public static String signed(int value) {
        if (value >= 0) return "+" + value;
        // Integer.MIN_VALUE não tem positivo: o texto do próprio número já traz o sinal.
        return value == Integer.MIN_VALUE ? MINUS + String.valueOf(value).substring(1) : MINUS + (-value);
    }

    /** "17", "−1": número para exibição, negativo com o sinal tipográfico (sem "+" no positivo). */
    public static String number(int value) {
        return value >= 0 ? String.valueOf(value) : signed(value);
    }

    /** "1d20+5", "1d20-2", "1d20" — a fórmula como o motor escreve ({@code %+d}, hífen ASCII). */
    public static String d20Formula(int bonus) {
        return bonus == 0 ? "1d20" : bonus > 0 ? "1d20+" + bonus : "1d20" + bonus;
    }

    /** Teste de d20 de verdade: rolagem livre ({@code 1d20} digitado) não tem crítico nem falha automática. */
    private static boolean d20Test(RollResult roll) {
        return !"free".equals(roll.kind()) && !roll.dice().isEmpty() && roll.dice().get(0).sides() == 20;
    }

    /** {@code SUCESSO}, {@code FALHA}, {@code CRÍTICO!}... ou vazio quando não há CD nem crítico. */
    public static String outcome(RollResult roll) {
        boolean d20 = d20Test(roll);
        if (roll.critical() || (d20 && roll.naturalMax())) {
            return Boolean.FALSE.equals(roll.success()) ? "CRÍTICO, MAS FALHA" : "CRÍTICO!";
        }
        if (d20 && roll.naturalOne() && !Boolean.TRUE.equals(roll.success())) return "FALHA CRÍTICA";
        // Repetição com CD: todas passam = SUCESSO, nenhuma = FALHA (as cores de sempre); misto = "1 DE 2 SUCESSOS".
        if (roll.repeated() && roll.hasTarget()) {
            int hits = roll.hits(), n = roll.repeats().size();
            return hits == n ? "SUCESSO" : hits == 0 ? "FALHA" : hits + " DE " + n + " SUCESSOS";
        }
        if (roll.success() == null) return "";
        return roll.success() ? "SUCESSO" : "FALHA";
    }

    /** "17 · 9" numa rolagem com repetição ({@code 2#1d20+5}); senão o total ("17", "−1"). */
    public static String totals(RollResult roll) {
        if (!roll.repeated()) return number(roll.total());
        StringBuilder out = new StringBuilder();
        for (RollResult.Repeat r : roll.repeats()) {
            if (!out.isEmpty()) out.append(" · ");
            out.append(number(r.total()));
        }
        return out.toString();
    }

    /**
     * O resultado em poucas palavras, para o resumo da mesa do mestre: "17 · sucesso", "17 · 9 · 1 de 2
     * sucessos", "9" (sem CD).
     */
    public static String resultSummary(RollResult roll) {
        String outcome = outcome(roll);
        return totals(roll) + (outcome.isEmpty() ? "" : " · " + outcome.toLowerCase(java.util.Locale.ROOT));
    }

    /** "2#1d20+5" → "1d20+5": a expressão de cada repetição. */
    private static String perRepeat(String formula) {
        int mark = formula.indexOf('#');
        return mark < 0 ? formula : formula.substring(mark + 1);
    }

    /**
     * "nat 12 + 5 = 17 · CD 15" — o que a linha de chat e o cartão mostram abaixo do nome. Com o
     * bônus escondido (NPC visto de fora): "nat 12 + ? = 17", para a conta não parecer errada.
     */
    public static String breakdown(RollResult roll, boolean bonusHidden, boolean dcHidden) {
        StringBuilder out = new StringBuilder();
        if (d20Test(roll)) {
            out.append("nat ").append(roll.natural());
            if (bonusHidden) {
                if (roll.total() != roll.natural()) out.append(" + ?");
            } else if (roll.bonus() != 0) {
                out.append(roll.bonus() > 0 ? " + " : " " + MINUS + " ").append(Math.abs((long) roll.bonus()));
            }
            out.append(" = ").append(number(roll.total()));
        } else if (roll.repeated()) {
            // "2× 1d20+5: 17 · 9 (soma 26)": cada rolagem e a soma (o número grande do cartão é o total).
            int n = roll.repeats().size();
            out.append(roll.formula().isEmpty() ? n + " rolagens" : n + "× " + perRepeat(roll.formula()))
                    .append(": ").append(totals(roll)).append(" (soma ").append(number(roll.total())).append(')');
        } else {
            if (!roll.formula().isEmpty()) out.append(roll.formula()).append(" = ");
            out.append(number(roll.total()));
        }
        if (roll.hasTarget()) out.append(" · CD ").append(roll.target());
        else if (dcHidden) out.append(" · CD oculta");
        return out.toString();
    }

    /**
     * Os dados de uma rolagem, para o tooltip: "4 · 1 · (6)" — o descartado (vantagem, keep/drop)
     * entre parênteses. Até {@code max} dados; o resto vira "…".
     */
    public static String diceList(RollResult roll, int max) {
        StringBuilder out = new StringBuilder();
        int shown = 0;
        if (roll.repeated()) {
            // Uma rolagem por grupo: "12  |  4" (2#1d20), "3 · 5  |  6 · 1" (2#2d6).
            for (RollResult.Repeat r : roll.repeats()) {
                if (!out.isEmpty()) out.append("  |  ");
                boolean first = true;
                for (RollResult.Die d : r.dice()) {
                    if (shown == max) return out.append(" · …").toString();
                    if (!first) out.append(" · ");
                    first = false;
                    out.append(d.dropped() ? "(" + d.value() + ")" : String.valueOf(d.value()));
                    shown++;
                }
            }
            return out.toString();
        }
        for (RollResult.Die d : roll.dice()) {
            if (shown == max) {
                out.append(" · …");
                break;
            }
            if (shown > 0) out.append(" · ");
            out.append(d.dropped() ? "(" + d.value() + ")" : String.valueOf(d.value()));
            shown++;
        }
        return out.toString();
    }

    /** "1 jogador", "3 jogadores", "nenhum jogador" — {@code plural} é a forma completa do plural. */
    public static String count(int n, String singular, String plural) {
        if (n == 0) return "nenhum " + singular;
        return n + " " + (Math.abs(n) == 1 ? singular : plural);
    }

    /** O plural certo, sem o número: {@code plural(n, "rolagem", "rolagens")}. */
    public static String plural(int n, String singular, String plural) {
        return Math.abs(n) == 1 ? singular : plural;
    }

    /** Tempo restante curto: "45 s", "3 min", "2 min 30 s". Negativo vira "0 s". */
    public static String duration(long seconds) {
        long s = Math.max(0, seconds);
        if (s < 60) return s + " s";
        long m = s / 60, rest = s % 60;
        return rest == 0 ? m + " min" : m + " min " + rest + " s";
    }

    /**
     * O {@code message} de erro do site como frase para o jogador. O backend devolve o texto do erro
     * Go: {@code "forbidden: o NPC está ligado a outro jogador"} (prefixo técnico sai, mesmo vários
     * encadeados como {@code "not_allowed: invalid: …"}) ou só o código
     * ({@code "character_in_other_encounter"}, que não é frase: vira vazio para quem chama usar o
     * próprio texto). Sem ponto final, para quem chama montar a frase.
     */
    public static String siteDetail(@Nullable String message) {
        String text = message == null ? "" : message.strip();
        for (int guard = 0; guard < 4; guard++) {
            var m = CODE_PREFIX.matcher(text);
            if (!m.find()) break;
            text = text.substring(m.end()).strip();
        }
        if (BARE_CODE.matcher(text).matches()) return "";
        while (text.endsWith(".")) text = text.substring(0, text.length() - 1).strip();
        return text;
    }

    /**
     * A frase de {@link #siteDetail} pronta para o jogador: inicial maiúscula e ponto final, ou
     * {@code fallback} quando o site só mandou o código.
     */
    public static String siteSentence(@Nullable String message, String fallback) {
        String detail = siteDetail(message);
        if (detail.isEmpty()) return fallback;
        return Character.toUpperCase(detail.charAt(0)) + detail.substring(1) + ".";
    }
}
