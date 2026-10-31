package com.aurorion.ficha.sheet;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogTest {

    @Test
    void mirrorsTheBackendCatalog() {
        assertEquals(6, Catalog.ATTRIBUTES.size());
        assertEquals(22, Catalog.SKILLS.size());
        assertEquals(List.of("for", "des", "vig", "sab", "int", "vit"), Catalog.ATTRIBUTES.stream().map(Catalog.Attribute::id).toList());
        for (Catalog.Skill s : Catalog.SKILLS) assertNotNull(Catalog.attribute(s.attribute()), s.id());
        assertEquals(28, new HashSet<>(Catalog.rollTargets()).size(), "ids das sugestões são únicos");
        int total = 0;
        for (Catalog.Attribute a : Catalog.ATTRIBUTES) total += Catalog.skillsOf(a.id()).size();
        assertEquals(22, total);
        assertEquals(6, Catalog.skillsOf("vit").size());
        assertEquals(1, Catalog.skillsOf("vig").size());
    }

    @Test
    void looksUpByIdLabelAndAlias() {
        assertEquals("furtividade", Catalog.skill("furtividade").id());
        assertEquals("investigacao", Catalog.skill("Investigação").id());
        assertEquals("corpo_a_corpo", Catalog.skill("melee").id());
        assertEquals("astros_e_mistos", Catalog.skill("Astros & Mistos").id());
        assertEquals("astros_e_mistos", Catalog.skill("astros").id());
        assertEquals("vit", Catalog.attribute("Vitral").id());
        assertEquals("int", Catalog.attribute("INT").id());
        assertNull(Catalog.skill("percepcao"));
        assertNull(Catalog.attribute("car"));
        assertEquals("astrosmistos", Catalog.normalize("Astros & Mistos"), "o & sai, como no NormalizeKey do backend");
    }

    @Test
    void conditionsHaveSeverityAndUnknownOnesStillShow() {
        assertEquals("moderada", Catalog.condition("medo").severity());
        assertEquals("Sangramento", Catalog.condition("sangramento").label());
        Catalog.Condition unknown = Catalog.condition("algo_novo");
        assertEquals("Algo novo", unknown.label());
        assertEquals("neutro", Catalog.elementClass("fogo"));
        assertEquals("solaris", Catalog.elementClass("solaris"));
        assertEquals("neutro", Catalog.elementClass(null));
    }

    @Test
    void texts() {
        assertEquals("Atletismo (DES)", Texts.testName("skill", "atletismo", "des"));
        assertEquals("Atletismo", Texts.testName("skill", "atletismo", "for"));
        assertEquals("Vigor", Texts.testName("attribute", "vig", ""));
        assertEquals("+3", Texts.signed(3));
        assertEquals("−2", Texts.signed(-2), "sinal de menos tipográfico, como no breakdown");
        assertEquals("+0", Texts.signed(0));

        RollResult success = new RollResult("skill", "Furtividade", List.of(new RollResult.Die(20, 12, false)), "normal",
                12, 5, 17, 15, true, false, "1d20+5");
        assertEquals("SUCESSO", Texts.outcome(success));
        assertEquals("nat 12 + 5 = 17 · CD 15", Texts.breakdown(success, false, false));
        assertEquals("nat 12 + ? = 17 · CD 15", Texts.breakdown(success, true, false),
                "bônus oculto não aparece, mas a conta não fica parecendo errada");
        RollResult hiddenDc = success.redacted(false, true);
        assertTrue(Texts.breakdown(hiddenDc, false, true).endsWith("CD oculta"));

        RollResult crit = new RollResult("skill", "x", List.of(new RollResult.Die(20, 20, false)), "normal", 20, 0, 20, 0, null, true, "");
        assertEquals("CRÍTICO!", Texts.outcome(crit));
        RollResult fumble = new RollResult("skill", "x", List.of(new RollResult.Die(20, 1, false)), "normal", 1, 3, 4, 10, false, false, "");
        assertEquals("FALHA CRÍTICA", Texts.outcome(fumble));
        RollResult plain = new RollResult("free", "2d6", List.of(new RollResult.Die(6, 3, false), new RollResult.Die(6, 4, false)),
                "normal", 3, 0, 7, 0, null, false, "2d6");
        assertEquals("", Texts.outcome(plain), "sem CD e sem d20: só o total");
        assertEquals("2d6 = 7", Texts.breakdown(plain, false, false));
    }
}
