package com.aurorion.ficha.sheet;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * O {@code Roll} de {@code /rpg/roll}: o {@code RollRecord} do motor achatado.
 *
 * <p>{@code target} 0 = sem CD; {@code success} {@code null} = sem CD para comparar (o cartão mostra
 * só o total). {@code mode}: {@code normal}, {@code vantagem} ou {@code desvantagem}. Nos dados,
 * {@code dropped} marca o d20 descartado pela vantagem/desvantagem.
 *
 * <p>{@code repeats}: a rolagem livre com repetição ({@code 2#1d20+5}, {@link DiceFormula}) traz uma
 * entrada por rolagem, com o total, a constante e os dados de cada uma; {@code total}, {@code bonus}
 * e {@code dice} de cima somam todas (a conta fecha para quem não conhece as repetições). Com CD, cada
 * repetição tem o seu {@code success}; o de cima é {@code true}/{@code false} só quando todas
 * concordam ({@code null} no misto, com {@code target} > 0). Vazio = uma rolagem só.
 */
public record RollResult(String kind, String label, List<Die> dice, String mode, int natural, int bonus, int total,
                         int target, @Nullable Boolean success, boolean critical, String formula, List<Repeat> repeats) {
    /** O motor aceita até 200 dados; o cartão mostra os primeiros (o total já vem somado). */
    public static final int MAX_DICE = 40;
    /** O teto de repetições do motor ({@link DiceFormula#MAX_REPEAT}). */
    public static final int MAX_REPEATS = DiceFormula.MAX_REPEAT;

    /** Imutável e sem nulo (só {@code success}, de propósito): o cartão cruza threads sem cópia. */
    public RollResult {
        kind = FichaSheet.text(kind);
        label = FichaSheet.text(label);
        dice = FichaSheet.list(dice);
        mode = mode == null || mode.isEmpty() ? "normal" : mode;
        formula = FichaSheet.text(formula);
        repeats = FichaSheet.list(repeats);
    }

    /** Uma rolagem só (sem repetição): a forma de sempre. */
    public RollResult(String kind, String label, List<Die> dice, String mode, int natural, int bonus, int total,
                      int target, @Nullable Boolean success, boolean critical, String formula) {
        this(kind, label, dice, mode, natural, bonus, total, target, success, critical, formula, List.of());
    }

    public record Die(int sides, int value, boolean dropped) {
    }

    /** Uma das repetições de {@code N#expr}: total, constante ({@code bonus}) e dados dela. */
    public record Repeat(int total, int bonus, List<Die> dice, @Nullable Boolean success) {
        public Repeat {
            dice = FichaSheet.list(dice);
        }
    }

    public boolean hasTarget() {
        return target > 0;
    }

    /** Mais de uma rolagem ({@code 2#1d20+5}). */
    public boolean repeated() {
        return repeats.size() > 1;
    }

    /** Quantas repetições chegaram na CD (numa rolagem só: 1 ou 0). */
    public int hits() {
        if (repeats.isEmpty()) return Boolean.TRUE.equals(success) ? 1 : 0;
        int n = 0;
        for (Repeat r : repeats) if (Boolean.TRUE.equals(r.success())) n++;
        return n;
    }

    /** Natural 20 num teste de d20 (o motor já marca {@code critical}; isto é só para a cor). */
    public boolean naturalMax() {
        return natural == 20;
    }

    public boolean naturalOne() {
        return natural == 1;
    }

    /**
     * A rolagem como um espectador a vê: sem o bônus (número privado de NPC) e/ou sem a CD (o mestre
     * escondeu). O total continua — é o que a mesa vê, como o {@code Public()} do motor.
     */
    public RollResult redacted(boolean hideBonus, boolean hideTarget) {
        if (!hideBonus && !hideTarget) return this;
        List<Repeat> reps = repeats;
        if (hideBonus && !reps.isEmpty()) {
            List<Repeat> out = new ArrayList<>(reps.size());
            for (Repeat r : reps) out.add(new Repeat(r.total(), 0, r.dice(), r.success()));
            reps = out;
        }
        return new RollResult(kind, label, dice, mode, natural, hideBonus ? 0 : bonus, total, hideTarget ? 0 : target,
                success, critical, hideBonus ? "" : formula, reps);
    }

    /** O site pode devolver a rolagem sem rótulo: o cartão usa o nome do teste. */
    public RollResult withLabel(@Nullable String fallback) {
        return label.isEmpty() && fallback != null && !fallback.isEmpty() ? relabeled(fallback) : this;
    }

    /** O mesmo resultado com outro rótulo (o pedido do mestre mostra o teste pedido, não o motivo). */
    public RollResult relabeled(String newLabel) {
        return new RollResult(kind, newLabel, dice, mode, natural, bonus, total, target, success, critical, formula, repeats);
    }

    /**
     * A CD aplicada aqui quando o site não a compara (site antigo: a rolagem livre volta sem
     * {@code target} nem {@code success}): o pedido de dados do mestre com CD compara o total
     * ({@code total ≥ CD}) — com repetição, cada uma por si, como o site faz. Já comparada (ou
     * {@code dc} 0), fica como veio.
     */
    public RollResult against(int dc) {
        if (dc <= 0 || target > 0 || success != null) return this;
        if (repeats.isEmpty()) {
            return new RollResult(kind, label, dice, mode, natural, bonus, total, dc, total >= dc, critical, formula, repeats);
        }
        List<Repeat> compared = new ArrayList<>(repeats.size());
        int hits = 0;
        for (Repeat r : repeats) {
            boolean ok = r.total() >= dc;
            if (ok) hits++;
            compared.add(new Repeat(r.total(), r.bonus(), r.dice(), ok));
        }
        Boolean all = hits == compared.size() ? Boolean.TRUE : hits == 0 ? Boolean.FALSE : null;
        return new RollResult(kind, label, dice, mode, natural, bonus, total, dc, all, critical, formula, compared);
    }

    @Nullable
    public static RollResult parse(@Nullable JsonObject o) {
        if (o == null) return null;
        List<Die> dice = dice(Json.array(o, "dice"), MAX_DICE);
        // As repetições dividem um teto de dados só (o cartão viaja num pacote com teto de tamanho).
        int[] budget = {MAX_DICE};
        List<Repeat> repeats = Json.objects(Json.array(o, "repeats"), MAX_REPEATS, r -> {
            List<Die> own = dice(Json.array(r, "dice"), budget[0]);
            budget[0] -= own.size();
            return new Repeat(Json.integer(r, "total"), Json.integer(r, "bonus"), own, Json.optionalBool(r, "success"));
        });
        String mode = Json.str(o, "mode", 16);
        return new RollResult(Json.str(o, "kind", 24), Json.str(o, "label", 80), dice,
                mode.isEmpty() ? "normal" : mode, Json.integer(o, "natural"), Json.integer(o, "bonus"),
                Json.integer(o, "total"), Math.max(0, Json.integer(o, "target")), Json.optionalBool(o, "success"),
                Json.bool(o, "critical"), Json.str(o, "formula", 80), repeats);
    }

    private static List<Die> dice(@Nullable JsonArray array, int max) {
        return List.copyOf(Json.objects(array, Math.max(0, max), d -> {
            Boolean kept = Json.optionalBool(d, "kept");
            boolean dropped = Json.bool(d, "dropped") || (kept != null && !kept);
            return new Die(Math.max(0, Json.integer(d, "sides")), Json.integer(d, "value"), dropped);
        }));
    }

    private static JsonArray diceJson(List<Die> dice) {
        JsonArray arr = new JsonArray();
        for (Die d : dice) {
            JsonObject j = new JsonObject();
            j.addProperty("sides", d.sides());
            j.addProperty("value", d.value());
            if (d.dropped()) j.addProperty("dropped", true);
            arr.add(j);
        }
        return arr;
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("kind", kind);
        o.addProperty("label", label);
        o.add("dice", diceJson(dice));
        o.addProperty("mode", mode);
        o.addProperty("natural", natural);
        o.addProperty("bonus", bonus);
        o.addProperty("total", total);
        o.addProperty("target", target);
        if (success != null) o.addProperty("success", success);
        o.addProperty("critical", critical);
        o.addProperty("formula", formula);
        if (!repeats.isEmpty()) {
            JsonArray reps = new JsonArray();
            for (Repeat r : repeats) {
                JsonObject j = new JsonObject();
                j.addProperty("total", r.total());
                j.addProperty("bonus", r.bonus());
                j.add("dice", diceJson(r.dice()));
                if (r.success() != null) j.addProperty("success", r.success());
                reps.add(j);
            }
            o.add("repeats", reps);
        }
        return o;
    }
}
