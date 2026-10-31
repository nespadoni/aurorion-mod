package com.aurorion.ficha.sheet;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SheetModelTest {

    /** O {@code GameSheet} do contrato, como o backend devolve em /rpg/sheet. */
    private static final String SHEET = """
            {
              "rpg_character_id": 1201, "subject_type": "sheet", "subject_id": 41,
              "name": "Lyra Vennamor", "title": "Cartógrafa", "portrait_url": "https://x/p.png",
              "site_path": "/profile/rpg/ficha/1201", "soul": 3, "legacy_level": 2,
              "element": {"id": "solaris", "label": "Solaris"}, "grade": {"value": 2, "label": "Marcado Vinculado"},
              "has_build": true, "progression_pending": false, "pending_review": false,
              "attributes": [{"id": "for", "label": "Força", "base": 2, "effective": 2},
                             {"id": "des", "label": "Destreza", "base": 3, "effective": 4}],
              "skills": [{"id": "furtividade", "label": "Furtividade", "attribute": "des", "value": 3, "bonus": 7},
                         {"id": "atletismo", "label": "Atletismo", "attribute": "for", "value": 1, "bonus": 3}],
              "derived": {"vida_max": 42, "stamina_max": 28, "sanidade_max": 30, "pe_max": 18, "defesa": 14, "rd": 2,
                          "movimento_m": 9, "iniciativa_bonus": 4, "fortitude": 13, "mark_charges": 2, "resonance_b": 3},
              "resources": {"initialized": true, "vida": 31, "stamina": 20, "sanidade": 17, "pe": 9, "temp_hp": 5,
                            "vital": "consciente", "exhaustion": 1, "conditions": ["sangramento", "medo"],
                            "charges_used": 1, "death_successes": 0, "death_failures": 0, "ultimate_available": true},
              "abilities": [{"name": "Lança de Aurora", "short_description": "Um feixe de luz.", "element": "solaris",
                             "rarity": "rara", "ability_level": 2, "equipped": true}],
              "lore": {"idade": "27", "origem": "Porto Velho", "alinhamento": "", "personalidade": "Curiosa", "aparencia": ""},
              "version": "r17-v4"
            }
            """;

    private static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    @Test
    void parsesEveryContractField() {
        FichaSheet s = FichaSheet.parse(json(SHEET));
        assertNotNull(s);
        assertEquals(1201, s.rpgCharacterId());
        assertEquals("sheet", s.subjectType());
        assertEquals(41, s.subjectId());
        assertEquals("Lyra Vennamor", s.name());
        assertEquals("/profile/rpg/ficha/1201", s.sitePath());
        assertEquals(3, s.soul());
        assertEquals(2, s.legacyLevel());
        assertEquals("solaris", s.element().id());
        assertEquals("Marcado Vinculado", s.grade().label());
        assertTrue(s.hasBuild());
        assertEquals(2, s.attributes().size());
        assertEquals(4, s.attribute("des").effective());
        assertEquals(7, s.skill("furtividade").bonus());
        assertEquals(42, s.derived().vidaMax());
        assertEquals(4, s.derived().iniciativaBonus());
        assertEquals(31, s.resources().vida());
        assertEquals(5, s.resources().tempHp());
        assertEquals(List.of("sangramento", "medo"), s.resources().conditions());
        assertTrue(s.resources().ultimateAvailable());
        assertEquals("Lança de Aurora", s.abilities().get(0).name());
        assertTrue(s.abilities().get(0).equipped());
        assertEquals("Porto Velho", s.lore().origem());
        assertEquals("r17-v4", s.version());
        assertFalse(s.npc());
    }

    @Test
    void roundTripKeepsTheSameSheet() {
        FichaSheet first = FichaSheet.parse(json(SHEET));
        FichaSheet second = FichaSheet.parse(json(Json.write(first.toJson())));
        assertEquals(first, second, "o servidor manda toJson e o cliente lê com o mesmo parse");
    }

    @Test
    void sheetWithoutBuildHasNullsAndEmptyLists() {
        FichaSheet s = FichaSheet.parse(json("""
                {"rpg_character_id": 9, "subject_type": "npc", "subject_id": 7, "name": "Bram",
                 "element": null, "grade": {"value": 0, "label": "Sem Marca"}, "has_build": false, "pending_review": true,
                 "attributes": [], "skills": null, "derived": null,
                 "resources": {"initialized": false, "conditions": []}, "abilities": [], "lore": {}}
                """));
        assertNotNull(s);
        assertFalse(s.hasBuild());
        assertTrue(s.pendingReview());
        assertNull(s.element());
        assertNull(s.derived());
        assertTrue(s.skills().isEmpty());
        assertTrue(s.lore().isEmpty());
        assertTrue(s.npc());
        FichaSheet again = FichaSheet.parse(json(Json.write(s.toJson())));
        assertEquals(s, again);
    }

    @Test
    void toleratesWrongTypesAndCapsSizes() {
        StringBuilder skills = new StringBuilder("[");
        for (int i = 0; i < 100; i++) skills.append(i == 0 ? "" : ",").append("{\"id\":\"s").append(i).append("\",\"bonus\":\"3\"}");
        skills.append("]");
        String longName = "x".repeat(500);
        FichaSheet s = FichaSheet.parse(json("{\"rpg_character_id\": \"12\", \"name\": \"" + longName + "\\nquebra\", \"soul\": 2.9,"
                + " \"has_build\": \"true\", \"skills\": " + skills + ", \"attributes\": [1, \"a\", {\"id\": \"\"}],"
                + " \"resources\": {\"conditions\": [\"medo\", {\"id\": \"caido\"}, 3, \"\"]}, \"version\": 5}"));
        assertNotNull(s);
        assertEquals(12, s.rpgCharacterId(), "número como texto");
        assertEquals(FichaSheet.MAX_NAME, s.name().length(), "nome cortado no teto");
        assertEquals(2, s.soul());
        assertTrue(s.hasBuild(), "booleano como texto");
        assertEquals(FichaSheet.MAX_SKILLS, s.skills().size(), "lista cortada no teto");
        assertEquals(3, s.skills().get(0).bonus());
        assertTrue(s.attributes().isEmpty(), "elementos que não são objeto (ou sem id) somem");
        assertEquals(List.of("medo", "caido", "3"), s.resources().conditions());
        assertEquals("5", s.version());
    }

    @Test
    void cleanCutsByCodePointAndDropsControls() {
        assertEquals("ab c", Json.clean("ab\u0007c", 10));
        String emoji = "\uD83D\uDD25\uD83D\uDD25\uD83D\uDD25";
        assertEquals("\uD83D\uDD25\uD83D\uDD25", Json.clean(emoji, 2), "não parte o par substituto");
        assertEquals("", Json.clean(null, 5));
    }

    @Test
    void bindingAndCandidates() {
        Binding b = Binding.parse(json("{\"source\": \"explicit\", \"subject_type\": \"npc\", \"subject_id\": 7, \"rpg_character_id\": 33, \"stale\": true}"));
        assertNotNull(b);
        assertTrue(b.explicit());
        assertTrue(b.stale());
        assertEquals(b, Binding.parse(b.toJson()));

        List<Candidate> list = Candidate.parseList(json("""
                {"c": [
                  {"subject_type": "sheet", "subject_id": 41, "kind": "sheet", "name": "Lyra", "title": "", "portrait_url": "",
                   "staff_only": false, "bound_profiles": []},
                  {"subject_type": "npc", "subject_id": 7, "kind": "npc", "name": "Bram", "staff_only": true,
                   "bound_profiles": ["Dev1_alt", "Outro"]},
                  {"subject_type": "", "subject_id": 0, "name": "inválido"}
                ]}""").getAsJsonArray("c"));
        assertEquals(2, list.size(), "candidato sem tipo/id é descartado");
        assertTrue(list.get(1).npc());
        assertTrue(list.get(1).staffOnly());
        assertEquals(List.of("Dev1_alt", "Outro"), list.get(1).boundProfiles());
        assertTrue(list.get(1).sameAs("npc", 7));
        assertEquals(list, Candidate.parseList(Candidate.toJson(list)));
    }

    @Test
    void rollParsesDroppedAndKeptAndRedacts() {
        RollResult r = RollResult.parse(json("""
                {"kind": "skill_test", "label": "Furtividade", "mode": "vantagem",
                 "dice": [{"sides": 20, "value": 17}, {"sides": 20, "value": 4, "dropped": true}, {"sides": 20, "value": 2, "kept": false}],
                 "natural": 17, "bonus": 5, "total": 22, "target": 15, "success": true, "critical": false, "formula": "1d20+5"}
                """));
        assertNotNull(r);
        assertFalse(r.dice().get(0).dropped());
        assertTrue(r.dice().get(1).dropped());
        assertTrue(r.dice().get(2).dropped(), "kept=false do RollRecord também conta como descartado");
        assertTrue(r.hasTarget());
        assertEquals(Boolean.TRUE, r.success());
        assertEquals(r, RollResult.parse(r.toJson()));

        RollResult hidden = r.redacted(true, true);
        assertEquals(0, hidden.bonus());
        assertEquals(0, hidden.target());
        assertEquals(22, hidden.total(), "o total continua: é o que a mesa vê");
        assertEquals(Boolean.TRUE, hidden.success());
        assertEquals("", hidden.formula(), "a fórmula entregaria o bônus");

        RollResult noDc = RollResult.parse(json("{\"natural\": 9, \"total\": 9, \"dice\": [{\"sides\": 20, \"value\": 9}]}"));
        assertNull(noDc.success(), "sem CD: nem sucesso nem falha");
        assertEquals("normal", noDc.mode());
        assertEquals("Atletismo", noDc.withLabel("Atletismo").label());
    }

    /**
     * Os tipos como o {@code GameSheet} do backend Go serializa de verdade ({@code internal/rpg/game.go}):
     * {@code legacy_level} texto, {@code rarity} inteiro (tier), {@code lore.idade} inteiro,
     * {@code version} hex de 16, {@code element} nulo.
     */
    @Test
    void parsesTheGoTypes() {
        FichaSheet s = FichaSheet.parse(json("""
                {"rpg_character_id": 1201, "subject_type": "sheet", "subject_id": 41, "name": "Lyra",
                 "legacy_level": "anomalia", "element": null, "grade": {"value": 0, "label": ""},
                 "abilities": [{"name": "Lança", "rarity": 3, "ability_level": 2, "equipped": true},
                               {"name": "Fora", "rarity": 0}],
                 "lore": {"idade": 0, "origem": "Porto"}, "version": "9f86d081884c7d65"}
                """));
        assertNotNull(s);
        assertEquals("anomalia", s.legacy());
        assertEquals(0, s.legacyLevel(), "texto não é número");
        assertEquals("★★★", s.abilities().get(0).rarity());
        assertEquals("", s.abilities().get(1).rarity(), "tier 0 = sem raridade");
        assertEquals("", s.lore().idade(), "idade 0 = não informada");
        assertEquals("9f86d081884c7d65", s.version());
        assertNull(s.element());
        assertEquals(s, FichaSheet.parse(json(Json.write(s.toJson()))), "ida e volta com os tipos do Go");

        FichaSheet numeric = FichaSheet.parse(json("{\"legacy_level\": 0, \"lore\": {\"idade\": 27}}"));
        assertEquals("", numeric.legacy());
        assertEquals("27", numeric.lore().idade());
        assertEquals(numeric, FichaSheet.parse(json(Json.write(numeric.toJson()))));
    }

    @Test
    void compatConstructorKeepsTheTestSheetWorking() {
        FichaSheet base = FichaSheet.parse(json(SHEET));
        FichaSheet compat = new FichaSheet(base.rpgCharacterId(), base.subjectType(), base.subjectId(), base.name(), base.title(),
                base.portraitUrl(), base.sitePath(), base.soul(), 3, base.element(), base.grade(), base.hasBuild(),
                base.progressionPending(), base.pendingReview(), base.attributes(), base.skills(), base.derived(),
                base.resources(), base.abilities(), base.lore(), base.version());
        assertEquals("3", compat.legacy());
        assertEquals(compat, FichaSheet.parse(json(Json.write(compat.toJson()))));
    }

    /** A {@code roll} como o Go achata: teste de NPC ({@code private}), livre sem CD ({@code success} nulo). */
    @Test
    void parsesTheGoRoll() {
        RollResult npc = RollResult.parse(json("""
                {"kind": "skill", "label": "Furtividade", "dice": [{"sides": 20, "value": 12}], "mode": "normal",
                 "natural": 12, "bonus": 6, "total": 18, "target": 15, "success": true, "critical": false,
                 "formula": "1d20+6", "private": true}
                """));
        assertNotNull(npc);
        assertEquals("skill", npc.kind());
        assertEquals(Boolean.TRUE, npc.success());

        RollResult free = RollResult.parse(json("""
                {"kind": "free", "label": "2d6+3", "dice": [{"sides": 6, "value": 4}, {"sides": 6, "value": 1, "dropped": true}],
                 "mode": "normal", "natural": 0, "bonus": 3, "total": 7, "target": 0, "success": null, "critical": false,
                 "formula": "2d6+3"}
                """));
        assertNotNull(free);
        assertNull(free.success(), "success nulo do Go: sem CD");
        assertFalse(free.hasTarget());
        assertEquals("2d6+3 = 7", Texts.breakdown(free, false, false));
        assertEquals("", Texts.outcome(free), "livre não tem sucesso, falha nem crítico");
    }

    @Test
    void siteDetailDropsTheGoPrefixAndBareCodes() {
        assertEquals("o NPC está ligado a outro jogador", Texts.siteDetail("forbidden: o NPC está ligado a outro jogador"));
        assertEquals("perícia \"xyz\"", Texts.siteDetail("invalid: perícia \"xyz\""));
        assertEquals("", Texts.siteDetail("character_in_other_encounter"), "só o código não é frase");
        assertEquals("muitas rolagens seguidas; tente de novo em instantes",
                Texts.siteDetail("muitas rolagens seguidas; tente de novo em instantes"));
        assertEquals("", Texts.siteDetail(""));
        assertEquals("", Texts.siteDetail(null));
        assertEquals("A CD vai de 0 a 100", Texts.siteDetail("A CD vai de 0 a 100."));
    }

    @Test
    void rollCardRoundTrip() {
        RollResult r = RollResult.parse(json("{\"label\": \"Força\", \"natural\": 20, \"total\": 23, \"critical\": true,"
                + " \"dice\": [{\"sides\": 20, \"value\": 20}]}"));
        RollCard card = new RollCard("cmd-1", "uuid", "Dev1", "Lyra", RollCard.GM, true, "ab12", true, false, r);
        RollCard back = RollCard.parse(json(Json.write(card.toJson())));
        assertEquals(card, back);
        assertTrue(back.gm());
        assertEquals("Lyra", back.displayName());
        assertNull(RollCard.parse(json("{\"id\": \"x\"}")), "cartão sem rolagem não existe");
    }
}
