package com.aurorion.core.character;

import net.minecraft.util.RandomSource;

/**
 * O teste da ficha feito no servidor: d20 + bônus contra uma CD.
 *
 * <ul>
 *   <li>total ≥ CD passa; total ≥ CD + {@link #CRIT_MARGIN}, ou 20 natural, é crítico;</li>
 *   <li>total &lt; CD falha; total ≤ CD − {@link #CRIT_MARGIN} é falha crítica;</li>
 *   <li>o 1 natural só derruba (falha crítica) quando a CD é ao menos {@code naturalMinDc} — tábua e
 *   graveto não podem falhar no dado;</li>
 *   <li>vantagem rola dois d20 e fica com o maior.</li>
 * </ul>
 *
 * <p>Utilidade compartilhada porque a magia e as profissões aplicam a mesma regra; a batalha de intenção
 * com ficha vai poder usar o {@code situational} para turno, reação e combo. Pura, sem estado, sem
 * alocação.
 */
public final class SheetCheck {
    public static final int DIE = 20;
    public static final int CRIT_MARGIN = 10;

    public enum Outcome {
        CRIT_FAIL, FAIL, SUCCESS, CRIT;

        public boolean success() {
            return this == SUCCESS || this == CRIT;
        }
    }

    private SheetCheck() {
    }

    public static Outcome roll(RandomSource random, int bonus, int dc, boolean advantage, int naturalMinDc) {
        return roll(random, bonus, 0, dc, advantage, naturalMinDc);
    }

    /** @param situational modificador do momento (cansaço, ajuda, combo), somado ao bônus da ficha */
    public static Outcome roll(RandomSource random, int bonus, int situational, int dc, boolean advantage,
                               int naturalMinDc) {
        int die = 1 + random.nextInt(DIE);
        if (advantage) die = Math.max(die, 1 + random.nextInt(DIE));
        return judge(die, bonus + situational, dc, naturalMinDc);
    }

    /** O resultado de um dado já rolado. */
    public static Outcome judge(int die, int bonus, int dc, int naturalMinDc) {
        if (die >= DIE) return Outcome.CRIT;
        if (die <= 1 && dc >= naturalMinDc) return Outcome.CRIT_FAIL;
        int total = die + bonus;
        if (total >= dc + CRIT_MARGIN) return Outcome.CRIT;
        if (total >= dc) return Outcome.SUCCESS;
        return total <= dc - CRIT_MARGIN ? Outcome.CRIT_FAIL : Outcome.FAIL;
    }

    /** Chance (0–1) de não passar, para mostrar na ficha. */
    public static double failChance(int bonus, int dc, boolean advantage, int naturalMinDc) {
        double fail = 0;
        for (int die = 1; die <= DIE; die++) {
            if (!judge(die, bonus, dc, naturalMinDc).success()) fail += weight(die, advantage);
        }
        return fail;
    }

    /** Chance (0–1) de crítico. */
    public static double critChance(int bonus, int dc, boolean advantage, int naturalMinDc) {
        double crit = 0;
        for (int die = 1; die <= DIE; die++) {
            if (judge(die, bonus, dc, naturalMinDc) == Outcome.CRIT) crit += weight(die, advantage);
        }
        return crit;
    }

    /** Probabilidade de o dado (ou o maior de dois) dar exatamente {@code die}. */
    private static double weight(int die, boolean advantage) {
        if (!advantage) return 1.0 / DIE;
        return (double) (die * die - (die - 1) * (die - 1)) / (DIE * DIE);
    }
}
