package com.aurorion.ficha.sheet;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * O catálogo do mod contra o código do motor: lê {@code rules/catalog.go} e {@code rules/conditions.go}
 * do {@code aurorion-web-backend} ao lado do repositório do mod, quando ele existe (máquina de
 * desenvolvimento). Sem o backend por perto (CI do mod), o teste de fonte não confere nada.
 */
class CatalogParityTest {

    private static Path backendRules() {
        Path dir = Path.of("").toAbsolutePath();
        for (int i = 0; i < 6 && dir != null; i++, dir = dir.getParent()) {
            Path rules = dir.resolve("aurorion-web-backend/internal/rpg/rules");
            if (Files.isDirectory(rules)) return rules;
        }
        return null;
    }

    @Test
    void matchesTheGoSource() throws IOException {
        Path rules = backendRules();
        if (rules == null) return;
        String catalog = Files.readString(rules.resolve("catalog.go"), StandardCharsets.UTF_8);

        Matcher skill = Pattern.compile("\\{\"([a-z_]+)\", \"([^\"]+)\", (FOR|DES|VIG|SAB|INT|VIT),").matcher(catalog);
        List<String> goSkills = new ArrayList<>();
        while (skill.find()) goSkills.add(skill.group(1) + "|" + skill.group(2) + "|" + skill.group(3).toLowerCase());
        assertEquals(Catalog.SKILLS.stream().map(s -> s.id() + "|" + s.label() + "|" + s.attribute()).toList(), goSkills,
                "perícias: id, nome, atributo padrão e ordem iguais ao rules.Skills");

        Matcher element = Pattern.compile("\\{(\\w+), \"([^\"]+)\", \\[2]Attr").matcher(catalog);
        List<String> goElements = new ArrayList<>();
        while (element.find()) goElements.add(element.group(2));
        assertEquals(Catalog.ELEMENT_LIST.stream().map(Catalog.Element::label).toList(), goElements, "Elementos");

        for (Catalog.Attribute a : Catalog.ATTRIBUTES) {
            assertTrue(catalog.contains(a.id().toUpperCase() + ": \"" + a.label() + "\""), "atributo " + a.id());
        }
        for (int g = 0; g <= 4; g++) assertTrue(catalog.contains("\"" + Catalog.gradeLabel(g) + "\""), "grau " + g);

        String conditions = Files.readString(rules.resolve("conditions.go"), StandardCharsets.UTF_8);
        Matcher c = Pattern.compile("\\{ID: \"([a-z_]+)\", Label: \"([^\"]+)\", Severity: (\\w+),[\\s\\S]*?Summary: \"([^\"]+)\", Removal: \"([^\"]+)\"")
                .matcher(conditions);
        List<String> goConditions = new ArrayList<>();
        while (c.find()) goConditions.add(c.group(1) + "|" + c.group(2) + "|" + severity(c.group(3)) + "|" + c.group(4) + "|" + c.group(5));
        assertEquals(Catalog.CONDITIONS.stream().map(x -> x.id() + "|" + x.label() + "|" + x.severity() + "|" + x.summary() + "|" + x.removal())
                .toList(), goConditions, "condições: id, nome, severidade e textos do motor");
    }

    private static String severity(String go) {
        return switch (go) {
            case "SeverityLight" -> "leve";
            case "SeverityModerate" -> "moderada";
            case "SeveritySevere" -> "grave";
            case "SeverityVital" -> "vital";
            case "SeverityState" -> "estado";
            default -> go;
        };
    }

    @Test
    void lookupsAreNullSafeAndPrebuilt() {
        assertNull(Catalog.skill(null));
        assertNull(Catalog.skill(""));
        assertNull(Catalog.attribute(null));
        assertEquals("", Catalog.normalize(null));
        assertEquals("", Catalog.attributeLabel(null));
        assertEquals("Vontade", Catalog.skillLabel("vontade"));
        assertEquals("pericia_nova", Catalog.skillLabel("pericia_nova"));
        assertEquals(Catalog.skillsOf("vit"), Catalog.skillsOf("Vitral"), "atributo por nome também");
        assertTrue(Catalog.skillsOf("car").isEmpty());
        assertTrue(Catalog.skillsOf(null).isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> Catalog.skillsOf("for").clear(), "lista compartilhada é imutável");
        assertThrows(UnsupportedOperationException.class, () -> Catalog.rollTargets().clear());
        assertEquals("astros_e_mistos", Catalog.skill("Astros e Místicos").id(), "grafia antiga com acento");
        assertEquals("sab", Catalog.attribute("sabedoria").id());
    }

    @Test
    void conditionsElementsAndLabels() {
        Catalog.Condition medo = Catalog.condition("medo");
        assertEquals("Fogo Vivo (Solaris), Vontade CD da fonte no fim do turno ou duração.", medo.removal());
        assertTrue(medo.tooltip().startsWith("Medo — Desvantagem"));
        assertTrue(medo.tooltip().contains("Como sair: Fogo Vivo"));
        assertEquals("Algo", Catalog.condition("algo").label());
        assertEquals("?", Catalog.condition(null).label());
        assertEquals("?", Catalog.condition("_").label());
        assertEquals("Algo novo", Catalog.condition("algo_novo").tooltip(), "sem resumo: só o nome");

        assertEquals("", Catalog.exhaustionSummary(0));
        assertEquals("Desvantagem em testes.", Catalog.exhaustionSummary(1));
        assertEquals("Desvantagem em testes e movimento à metade.", Catalog.exhaustionSummary(2));
        assertEquals(Catalog.exhaustionSummary(3), Catalog.exhaustionSummary(9), "nível acima do teto = o teto");

        assertEquals("quantico", Catalog.elementClass("Quântico"));
        assertEquals("Determinação", Catalog.elementLabel("determinacao"));
        assertEquals("Fogo", Catalog.elementLabel("fogo"));
        assertEquals("", Catalog.elementLabel(null));
        assertNotNull(Catalog.element("CHAOS"));
        assertEquals(11, Catalog.ELEMENTS.size());

        assertEquals("Anomalia", Catalog.legacyLabel("anomalia"));
        assertEquals("Celestial", Catalog.legacyLabel("CELESTIAL"));
        assertEquals("3", Catalog.legacyLabel("3"));
        assertEquals("", Catalog.legacyLabel(""));
        assertEquals("", Catalog.vitalLabel("consciente"));
        assertEquals("Em queda", Catalog.vitalLabel("queda"));
        assertEquals("Marcado Desperto", Catalog.gradeLabel(1));
        assertEquals("", Catalog.gradeLabel(7));
    }

    @Test
    void textHelpers() {
        assertEquals("−2147483648", Texts.signed(Integer.MIN_VALUE));
        assertEquals("−1", Texts.number(-1));
        assertEquals("17", Texts.number(17));
        assertEquals("1d20", Texts.d20Formula(0));
        assertEquals("1d20+5", Texts.d20Formula(5));

        assertEquals("nenhum jogador", Texts.count(0, "jogador", "jogadores"));
        assertEquals("1 jogador", Texts.count(1, "jogador", "jogadores"));
        assertEquals("3 rolagens", Texts.count(3, "rolagem", "rolagens"));
        assertEquals("pedidos", Texts.plural(2, "pedido", "pedidos"));
        assertEquals("45 s", Texts.duration(45));
        assertEquals("3 min", Texts.duration(180));
        assertEquals("2 min 30 s", Texts.duration(150));
        assertEquals("0 s", Texts.duration(-4));

        assertEquals("vantagem/desvantagem é concedida pelo mestre",
                Texts.siteDetail("not_allowed: vantagem/desvantagem é concedida pelo mestre"));
        assertEquals("Lyra está em um encontro em andamento",
                Texts.siteDetail("forbidden: character_in_other_encounter: Lyra está em um encontro em andamento"),
                "prefixos encadeados saem todos");
        assertEquals("rolagem inválida: fórmula vazia", Texts.siteDetail("rolagem inválida: fórmula vazia"),
                "o prefixo com espaço já é frase");
        assertEquals("O NPC está ligado a outro jogador.",
                Texts.siteSentence("forbidden: o NPC está ligado a outro jogador", "Sem permissão."));
        assertEquals("Sem permissão.", Texts.siteSentence("forbidden", "Sem permissão."));

        assertEquals("", Texts.testName(null, null, null));
        assertEquals("Atletismo", Texts.testName("skill", "atletismo", "Força"), "atributo por nome = o padrão");
        assertEquals("Furtividade (VIT)", Texts.testName("skill", "Furtividade", "vit"));
        assertEquals("", Texts.modeLabel(null));
    }
}
