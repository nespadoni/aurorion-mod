package com.aurorion.intencao.combat;

import com.aurorion.core.character.CharacterSheet;
import com.aurorion.core.character.CharacterSpells;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.UUID;

/**
 * Um lado de carne e osso do duelo: os números da ficha (retrato do {@code aurorion-ficha}) copiados no
 * começo, e a vida e o PE que só valem dentro do duelo. Sem ficha lida, valores de iniciante — ninguém
 * fica de fora por falta de ficha, mas quem tem ficha boa luta melhor.
 */
final class Combatant {
    final UUID id;
    final String name;
    final int side;
    final int color;
    final int hpMax, peMax, defense, rd, initiative;
    final int melee, ranged, dodge, parry, strength, dexterity;
    final List<CharacterSheet.Technique> techniques;
    final List<CharacterSpells.Option> spells;

    int hp, pe;
    /** Defendendo até o próximo turno dele: defesa +3 e metade do dano. */
    boolean defending;
    /** Errou feio (falha crítica no ataque): defesa −2 até o próximo turno dele. */
    boolean stumbling;
    /** Falha crítica numa magia: perde o próximo turno. */
    boolean exhausted;
    /** Saiu do jogo ou desistiu: conta como derrotado. */
    boolean out;
    /** Desistiu pelo menu ou pelo comando: nunca morre por isso, nem no duelo até a morte. */
    boolean surrendered;
    int initiativeRoll;
    /** Defesas bem-sucedidas em seguida: cada uma tira {@code guardaCansada} da próxima. */
    int guardStreak;
    /** O nível da alma (1–30) e a Escala da Alma E = Vida de referência / 150 (RPG v0.3 §04). */
    final int soul;
    final double scale;
    // O placar do resultado.
    int dealt, taken, hits, defended, crits;

    /** Vida de referência por nível da alma (RPG v0.3 §04), índice = alma − 1. */
    static final int[] REFERENCE_LIFE = {110, 170, 250, 320, 400, 500, 600, 720, 860, 1020, 1210, 1410, 1640, 1900, 2200,
            2550, 2950, 3400, 3920, 4520, 5250, 6150, 7250, 8550, 10050, 12050, 14550, 18050, 23050, 30050};
    /** d12 por pacote (RPG v0.3 §09): básico 3; H1 4, H2 5, H3 6, H4 8, H5 Ultimate 12. */
    static final int BASIC_DICE = 3;
    static final int[] TECHNIQUE_DICE = {4, 5, 6, 8, 12};

    /** A alma da ficha; sem ela (ficha antiga), a alma cuja Vida de referência fica mais perto da vida máxima. */
    static int soulFor(int soul, int hpMax) {
        if (soul >= 1) return Math.min(REFERENCE_LIFE.length, soul);
        int best = 1;
        for (int i = 0; i < REFERENCE_LIFE.length; i++) {
            if (Math.abs(REFERENCE_LIFE[i] - hpMax) < Math.abs(REFERENCE_LIFE[best - 1] - hpMax)) best = i + 1;
        }
        return best;
    }

    static double scaleFor(int soul) {
        return REFERENCE_LIFE[Math.max(1, Math.min(REFERENCE_LIFE.length, soul)) - 1] / 150.0;
    }

    /** Dano bruto = piso((soma dos d12 + MO) × E): o MO entra uma vez no pacote, nunca por dado (§09). */
    static int damage(int diceSum, int mod, double scale) {
        return Math.max(0, (int) Math.floor((diceSum + mod) * scale));
    }

    private Combatant(UUID id, String name, int side, int color, CharacterSheet.Stats stats, List<CharacterSpells.Option> spells) {
        this.id = id;
        this.name = name;
        this.side = side;
        this.color = color;
        boolean known = stats.known();
        this.hpMax = Math.max(1, known ? stats.derived("vida_max", 20) : 20);
        this.soul = soulFor(known ? stats.derived("alma", 0) : 0, hpMax);
        this.scale = scaleFor(soul);
        this.peMax = Math.max(0, known ? stats.derived("pe_max", 4) : 4);
        this.defense = known ? stats.derived("defesa", 10) : 10;
        this.rd = Math.max(0, known ? stats.derived("rd", 0) : 0);
        this.initiative = known ? stats.derived("iniciativa", 0) : 0;
        this.melee = known ? stats.skillBonus("corpo_a_corpo", 1) : 2;
        this.ranged = known ? stats.skillBonus("pontaria", 1) : 2;
        this.dodge = known ? stats.skillBonus("acrobacia", 1) : 2;
        this.parry = known ? stats.skillBonus("corpo_a_corpo", 1) : 2;
        this.strength = known ? stats.attribute("for", 1) : 1;
        this.dexterity = known ? stats.attribute("des", 1) : 1;
        this.techniques = List.copyOf(stats.techniques());
        this.spells = List.copyOf(spells);
        this.hp = hpMax;
        this.pe = peMax;
    }

    static Combatant of(ServerPlayer player, String name, int side, int color) {
        CharacterSheet.Stats stats = CharacterSheet.of(player.server, player.getUUID());
        return new Combatant(player.getUUID(), name, side, color, stats, CharacterSpells.of(player));
    }

    /**
     * Derrotado pelo limite que os dois escolheram: {@code -1} primeiro sangue (qualquer dano), {@code 0}
     * até a morte (vida zerada), senão a porcentagem da vida máxima.
     */
    boolean defeated(int limit) {
        if (out) return true;
        if (limit < 0) return hp < hpMax;
        if (limit == 0) return hp <= 0;
        return hp <= stopHp(limit);
    }

    int stopHp(int limit) {
        if (limit < 0) return hpMax - 1;
        return (int) Math.ceil(hpMax * limit / 100.0);
    }

    int effectiveDefense() {
        return defense + (defending ? 3 : 0) - (stumbling ? 2 : 0);
    }
}
