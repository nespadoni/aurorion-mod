package com.aurorion.ficha.sheet;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A gramática do {@code roll.ParseFormula} do site: o que ele aceita sai canônico; o que ele recusa, aqui também. */
class DiceFormulaTest {

    @Test
    void acceptsWhatTheEngineAcceptsInCanonicalForm() {
        assertEquals("1d20", DiceFormula.normalize("d20"));
        assertEquals("1d20", DiceFormula.normalize("1D20"));
        assertEquals("2d6+3", DiceFormula.normalize(" 2d6 + 3 "));
        assertEquals("1d20-2", DiceFormula.normalize("1d20-2"));
        assertEquals("1d100", DiceFormula.normalize("d%"), "d% é d100, como String() do motor escreve");
        assertEquals("4d6kh3", DiceFormula.normalize("4d6kh3"));
        assertEquals("2d20kl1", DiceFormula.normalize("2d20kl1"));
        assertEquals("4d6dl1", DiceFormula.normalize("4d6dl1"));
        assertEquals("-1d4+1d6", DiceFormula.normalize("-1d4+1d6"), "a primeira parcela pode ser negativa");
        assertEquals("1d6+0", DiceFormula.normalize("1d6+0"));
        assertEquals("7d6", DiceFormula.normalize("007d6"), "zeros à esquerda, como o Atoi");
        assertEquals("3+1d8", DiceFormula.normalize("3+1d8"), "constante antes do dado");
    }

    @Test
    void refusesWhatTheEngineRefuses() {
        for (String bad : List.of("", "   ", "20", "1+2", "2d6*3", "(1d6)", "1d6+", "1d6+-2", "++1d6", "0d6", "1d1", "1d1001",
                "1d", "dd6", "2d6x", "4d6kh", "4d6kh0", "2d6kh3", "2d6dh2", "2d6dl3", "1d20+100001",
                "201d6", "100d6+101d6", "1d6+1d6+1d6+1d6+1d6+1d6+1d6+1d6+1d6+1d6+1d6", "furtividade", "Destreza (DES)")) {
            assertNull(DiceFormula.normalize(bad), bad);
            assertNotNull(DiceFormula.problem(bad), "toda recusa diz o motivo: " + bad);
        }
        assertNull(DiceFormula.normalize("1d6+" + "1".repeat(DiceFormula.MAX_LENGTH)), "comprimento acima do teto do mod");
        assertNull(DiceFormula.problem("2d6+3"));
    }

    @Test
    void limitsMatchTheEngine() {
        assertNotNull(DiceFormula.normalize("200d6"), "200 dados é o teto");
        assertNotNull(DiceFormula.normalize("100d6+100d4"));
        assertNotNull(DiceFormula.normalize("1d1000"));
        assertNotNull(DiceFormula.normalize("1d20+100000"));
        assertNotNull(DiceFormula.normalize("2d6kh2"), "manter todos vale");
        assertNotNull(DiceFormula.normalize("1d6+1d6+1d6+1d6+1d6+1d6+1d6+1d6+1d6+1d6"), "10 parcelas");
    }

    @Test
    void commonNotationsPlayersType() {
        assertEquals("1d20+5", DiceFormula.normalize("d20+5"));
        assertEquals("1d20+5", DiceFormula.normalize("1d20 + 5"));
        assertEquals("2d20kh1+5", DiceFormula.normalize("2d20kh1+5"));
        assertEquals("4d6kh3", DiceFormula.normalize("4d6kh3"));
        assertEquals("1d100", DiceFormula.normalize("d%"));
        assertEquals("1d20", DiceFormula.normalize("1D20"));
        // "+5" sozinho e "kh" sem número: recusa com o motivo e o formato aceito.
        for (String bad : List.of("+5", "2d20kh", "1d20x2", "d20 cd 15")) {
            String why = DiceFormula.explain(bad);
            assertNotNull(why, bad);
            assertTrue(why.startsWith("Fórmula inválida: ") && why.contains("1d20+5") && why.contains("2#d20+5"), why);
        }
        assertNull(DiceFormula.explain("2#d20+5"));
    }

    @Test
    void repeatLikeRoll20() {
        assertEquals("2#1d20+5", DiceFormula.normalize("2#d20+5"), "o caso do usuário");
        assertEquals("2#1d20+5", DiceFormula.normalize(" 2 # d20 + 5 "));
        assertEquals("3#1d6", DiceFormula.normalize("3x1d6"), "x é sinônimo no começo (cabe numa palavra do Brigadier)");
        assertEquals("2#1d20", DiceFormula.normalize("2X d20"));
        assertEquals("1d20", DiceFormula.normalize("1#d20"), "1# é uma rolagem só");
        assertEquals("10#20d6", DiceFormula.normalize("10#20d6"), "200 dados somando as repetições");
        assertEquals("2#4d6kh3-1", DiceFormula.normalize("2#4d6kh3-1"));
        assertEquals(2, DiceFormula.repeat("2#d20+5"));
        assertEquals(1, DiceFormula.repeat("1d20"));
        assertEquals(1, DiceFormula.repeat("lixo"));
        assertTrue(Texts.isDice("2#1d20+5"));
        assertEquals("Rolar 2#1d20+5", Texts.askHeadline("2#1d20+5"));
        assertFalse(DiceFormula.singleD20("2#1d20+5"), "duas rolagens não leem a CD como um teste só");
        for (String bad : List.of("0#d20", "11#d20", "#d20", "2#", "d20#2", "2#3#d6", "-2#d20", "+2#d20", "2#5", "10#21d6", "a#d20")) {
            assertNull(DiceFormula.normalize(bad), bad);
            assertNotNull(DiceFormula.problem(bad), bad);
        }
    }

    @Test
    void repeatedRollFromTheSite() {
        // A resposta do /rpg/roll do site para "2#d20+5" com CD 15 (formato exato de game.go).
        RollResult r = RollResult.parse(com.google.gson.JsonParser.parseString("""
                {"bonus":10,"critical":false,"dice":[{"sides":20,"value":1},{"sides":20,"value":19}],"formula":"2#1d20+5",
                 "kind":"free","label":"2#1d20+5","mode":"normal","natural":0,
                 "repeats":[{"bonus":5,"dice":[{"sides":20,"value":1}],"success":false,"total":6},
                            {"bonus":5,"dice":[{"sides":20,"value":19}],"success":true,"total":24}],
                 "success":null,"target":15,"total":30}""").getAsJsonObject());
        assertNotNull(r);
        assertTrue(r.repeated());
        assertEquals(2, r.repeats().size());
        assertEquals(24, r.repeats().get(1).total());
        assertEquals(1, r.hits());
        assertNull(r.success(), "misto");
        assertEquals("6 · 24", Texts.totals(r));
        assertEquals("1 DE 2 SUCESSOS", Texts.outcome(r));
        assertEquals("2× 1d20+5: 6 · 24 (soma 30) · CD 15", Texts.breakdown(r, false, false));
        assertEquals("6 · 24 · 1 de 2 sucessos", Texts.resultSummary(r));
        assertEquals("1  |  19", Texts.diceList(r, 10));
        assertEquals(r, RollResult.parse(r.toJson()), "ida e volta pelo pacote do cartão");
        assertSame(r, r.against(15), "o site já comparou");
        RollResult hidden = r.redacted(true, true);
        assertEquals(0, hidden.target());
        assertEquals("2 rolagens: 6 · 24 (soma 30) · CD oculta", Texts.breakdown(hidden, true, true));
        assertEquals(0, hidden.repeats().get(0).bonus());
    }

    @Test
    void repeatedRollAgainstTheGmDcWhenTheSiteDidNotCompare() {
        RollResult.Repeat a = new RollResult.Repeat(17, 5, List.of(new RollResult.Die(20, 12, false)), null);
        RollResult.Repeat b = new RollResult.Repeat(9, 5, List.of(new RollResult.Die(20, 4, false)), null);
        RollResult free = new RollResult("free", "2#1d20+5", List.of(a.dice().get(0), b.dice().get(0)), "normal", 0, 10, 26, 0, null,
                false, "2#1d20+5", List.of(a, b));
        assertEquals("2× 1d20+5: 17 · 9 (soma 26)", Texts.breakdown(free, false, false));
        assertEquals("", Texts.outcome(free), "sem CD não tem resultado");
        RollResult mixed = free.against(15);
        assertEquals(15, mixed.target());
        assertNull(mixed.success());
        assertEquals(Boolean.TRUE, mixed.repeats().get(0).success());
        assertEquals(Boolean.FALSE, mixed.repeats().get(1).success());
        assertEquals("1 DE 2 SUCESSOS", Texts.outcome(mixed));
        assertSame(mixed, mixed.against(15), "já comparada não muda");
        assertEquals("SUCESSO", Texts.outcome(free.against(9)), "todas passam: a cor de sucesso de sempre");
        assertEquals(Boolean.TRUE, free.against(9).success());
        assertEquals("FALHA", Texts.outcome(free.against(18)));
        assertEquals(Boolean.FALSE, free.against(18).success());
        assertEquals("17 · 9", Texts.totals(free));
        assertEquals("17", Texts.totals(free.against(0).relabeled("x").withLabel(null)).substring(0, 2));
    }

    @Test
    void singleD20IsWhereADcReadsLikeATest() {
        assertTrue(DiceFormula.singleD20("1d20"));
        assertTrue(DiceFormula.singleD20("d20+3"));
        assertFalse(DiceFormula.singleD20("2d20kh1"));
        assertFalse(DiceFormula.singleD20("2d6"));
        assertFalse(DiceFormula.singleD20("1d20+1d4"));
        assertFalse(DiceFormula.singleD20("lixo"));
    }

    @Test
    void askHeadlineTellsDiceFromTests() {
        assertEquals("Rolar 2d6", Texts.askHeadline("2d6"));
        assertEquals("Rolar 1d20+2", Texts.askHeadline("1d20+2"));
        assertEquals("Teste de Furtividade", Texts.askHeadline("Furtividade"));
        assertEquals("Teste de Atletismo (DES)", Texts.askHeadline("Atletismo (DES)"));
        for (Catalog.Skill skill : Catalog.SKILLS) assertFalse(Texts.isDice(skill.label()), skill.label());
        for (Catalog.Attribute a : Catalog.ATTRIBUTES) assertFalse(Texts.isDice(a.label()), a.label());
    }

    @Test
    void freeRollAgainstTheGmDc() {
        RollResult free = new RollResult("free", "2d6", List.of(new RollResult.Die(6, 3, false), new RollResult.Die(6, 4, false)),
                "normal", 3, 0, 7, 0, null, false, "2d6");
        RollResult hit = free.against(7);
        assertEquals(7, hit.target());
        assertEquals(Boolean.TRUE, hit.success(), "total ≥ CD");
        assertEquals("SUCESSO", Texts.outcome(hit));
        assertEquals("2d6 = 7 · CD 7", Texts.breakdown(hit, false, false));
        RollResult miss = free.against(8);
        assertEquals(Boolean.FALSE, miss.success());
        assertEquals("FALHA", Texts.outcome(miss));
        assertSame(free, free.against(0), "sem CD fica como veio");
        assertSame(hit, hit.against(20), "já comparada não muda");
        assertEquals("", Texts.outcome(free.relabeled("1d20").against(0)), "rolagem livre sem CD não tem resultado");
        assertEquals("1d20", free.relabeled("1d20").label());
    }
}
