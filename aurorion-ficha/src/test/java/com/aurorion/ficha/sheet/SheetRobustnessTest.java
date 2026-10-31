package com.aurorion.ficha.sheet;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.aurorion.ficha.sheet.GoFixtures.json;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** O parse com a resposta real do Go e com tudo o que pode vir torto: nunca exceção, sempre teto. */
class SheetRobustnessTest {

    @Test
    void fullGoSheetMatchesTheCatalog() {
        FichaSheet s = FichaSheet.parse(json(GoFixtures.SHEET_WITH_BUILD));
        assertNotNull(s);
        assertEquals(6, s.attributes().size());
        assertEquals(22, s.skills().size());
        for (int i = 0; i < 22; i++) {
            Catalog.Skill c = Catalog.SKILLS.get(i);
            FichaSheet.Skill k = s.skills().get(i);
            assertEquals(c.id(), k.id(), "ordem de rules.Skills");
            assertEquals(c.label(), k.label(), "nome com acento igual ao do motor");
            assertEquals(c.attribute(), k.attribute(), "atributo padrão igual ao do motor");
        }
        Map<String, List<FichaSheet.Skill>> groups = s.skillsByAttribute();
        assertEquals(List.of("for", "des", "vig", "sab", "int", "vit"), List.copyOf(groups.keySet()));
        assertEquals(List.of(2, 3, 1, 6, 4, 6), groups.values().stream().map(List::size).toList());
        assertEquals("entidade", s.legacy());
        assertEquals("Entidade", Catalog.legacyLabel(s.legacy()));
        assertEquals("★★★", s.abilities().get(0).rarity());
        assertEquals("★★★★★", s.abilities().get(1).rarity());
        assertEquals("martirio", Catalog.elementClass(s.abilities().get(1).element()),
                "o códex grava \"martírio\" com acento: a cor do elemento não pode cair no neutro");
        assertEquals("Martírio", Catalog.elementLabel(s.abilities().get(1).element()));
        assertEquals("27", s.lore().idade());
        assertEquals("https://aurorion.example/uploads/sheets/41.png", s.portraitUrl());
        assertEquals(s, FichaSheet.parse(json(Json.write(s.toJson()))), "servidor → cliente sem perder nada");
    }

    @Test
    void npcWithoutBuildFromGo() {
        FichaSheet s = FichaSheet.parse(json(GoFixtures.NPC_WITHOUT_BUILD));
        assertNotNull(s);
        assertTrue(s.npc());
        assertFalse(s.hasBuild());
        assertTrue(s.pendingReview());
        assertNull(s.derived());
        assertNull(s.element());
        assertTrue(s.skillsByAttribute().isEmpty());
        assertEquals("", s.lore().idade(), "idade 0 do Go = não informada");
        assertTrue(s.lore().isEmpty());
        assertEquals("Sem Marca", s.grade().label());
        assertEquals(s, FichaSheet.parse(json(Json.write(s.toJson()))));
    }

    @Test
    void missingLabelsComeFromTheCatalog() {
        FichaSheet s = FichaSheet.parse(json("""
                {"attributes": [{"id": "des", "base": 3, "effective": 4}, {"id": "des", "label": "Duplicado"}],
                 "skills": [{"id": "investigacao", "bonus": 4}, {"id": "investigacao", "bonus": 99}, {"id": "nova_pericia", "attribute": "int"}],
                 "grade": {"value": 3}, "element": "anacronico"}
                """));
        assertNotNull(s);
        assertEquals(1, s.attributes().size(), "id repetido entra uma vez");
        assertEquals("Destreza", s.attributes().get(0).label());
        assertEquals(2, s.skills().size());
        assertEquals("Investigação", s.skills().get(0).label());
        assertEquals("int", s.skills().get(0).attribute(), "atributo padrão do catálogo");
        assertEquals(4, s.skills().get(0).bonus(), "a primeira ocorrência vale");
        assertEquals("nova_pericia", s.skills().get(1).label(), "perícia nova aparece com o id");
        assertEquals("Marcado Primaz", s.grade().label(), "rótulo do grau do motor");
        assertNotNull(s.element(), "element só com o id em texto também vale");
        assertEquals("Anacrônico", s.element().label());
    }

    @Test
    void wrongTypesBecomeNeutral() {
        FichaSheet s = assertDoesNotThrow(() -> FichaSheet.parse(json("""
                {"rpg_character_id": "12.0", "subject_id": 1e999999, "name": true, "title": {"x": 1}, "soul": "NaN",
                 "legacy_level": 7, "has_build": 1, "pending_review": "0", "version": 123456789012,
                 "attributes": "for", "skills": {"id": "x"}, "derived": [], "resources": "cheio",
                 "abilities": [null, 3, {"name": ["a"], "rarity": "9", "ability_level": "2", "equipped": "true"}],
                 "lore": {"idade": -1, "origem": 42}}
                """)));
        assertNotNull(s);
        assertEquals(12, s.rpgCharacterId(), "\"12.0\" vale 12");
        assertTrue(s.subjectId() > 0, "1e999999 satura em vez de travar no BigDecimal");
        assertEquals("", s.name(), "booleano não é nome");
        assertEquals("", s.title());
        assertEquals(0, s.soul());
        assertEquals("7", s.legacy());
        assertEquals(7, s.legacyLevel());
        assertTrue(s.hasBuild(), "1 = verdadeiro");
        assertFalse(s.pendingReview());
        assertEquals("123456789012", s.version());
        assertTrue(s.attributes().isEmpty());
        assertTrue(s.skills().isEmpty());
        assertNull(s.derived());
        assertEquals(FichaSheet.Resources.EMPTY, s.resources());
        assertEquals(1, s.abilities().size());
        assertEquals("", s.abilities().get(0).name());
        assertEquals("", s.abilities().get(0).rarity(), "tier fora de 1–5 some");
        assertEquals(2, s.abilities().get(0).abilityLevel());
        assertTrue(s.abilities().get(0).equipped());
        assertEquals("", s.lore().idade());
        assertEquals("42", s.lore().origem());
    }

    @Test
    void hugeListsAndStringsAreCapped() {
        StringBuilder abilities = new StringBuilder("[");
        for (int i = 0; i < 5000; i++) abilities.append(i == 0 ? "" : ",").append("{\"name\":\"T").append(i).append("\"}");
        StringBuilder conditions = new StringBuilder("[");
        for (int i = 0; i < 500; i++) conditions.append(i == 0 ? "" : ",").append("\"c").append(i).append('"');
        String huge = "á".repeat(100_000);
        FichaSheet s = FichaSheet.parse(json("{\"name\":\"" + huge + "\",\"abilities\":" + abilities + "],"
                + "\"resources\":{\"conditions\":" + conditions + "]},\"lore\":{\"aparencia\":\"" + huge + "\"}}"));
        assertNotNull(s);
        assertEquals(FichaSheet.MAX_ABILITIES, s.abilities().size());
        assertEquals(FichaSheet.MAX_CONDITIONS, s.resources().conditions().size());
        assertEquals(FichaSheet.MAX_NAME, s.name().codePointCount(0, s.name().length()));
        assertEquals(FichaSheet.MAX_LORE, s.lore().aparencia().length());
        assertTrue(Json.write(s.toJson()).length() < 64 * 1024, "o pacote para o cliente fica pequeno");
    }

    @Test
    void formattingCodesAndInvisibleCharactersAreRemoved() {
        assertEquals("Lyra", Json.clean("§c§lLyra§r", 40), "§x não vira cor no chat");
        assertEquals("Lyra", Json.clean("§kLyra§", 40));
        assertEquals("ab", Json.clean("a‮b​", 40), "inversão bidirecional e largura zero saem");
        assertEquals("a b", Json.clean("  a \n\t\n b  ", 40), "espaços e quebras seguidos viram um");
        assertEquals("a�b", Json.clean("a\uD800b", 40), "metade solta de par substituto");
        assertEquals("Ação ✦ 🔥", Json.clean("Ação ✦ 🔥", 40), "unicode normal passa");
        assertEquals("", Json.clean("abc", 0));
        assertEquals("abcd…", Json.clip("abcdefgh", 5), "cortado mostra reticências e não passa do teto");
        assertEquals("abcde", Json.clip("abcde", 5), "no teto exato não corta");
        assertEquals("ab…", Json.clip("ab  cdef", 4), "sem espaço antes das reticências");

        FichaSheet s = FichaSheet.parse(json("{\"name\":\"§4Lorde\\u00a7k Sombrio\",\"resources\":{\"conditions\":[\"§amedo\"]}}"));
        assertEquals("Lorde Sombrio", s.name());
        assertEquals(List.of("medo"), s.resources().conditions());
    }

    @Test
    void linksAreDroppedInsteadOfCut() {
        String longUrl = "https://x.example/" + "a".repeat(FichaSheet.MAX_URL);
        FichaSheet s = FichaSheet.parse(json("{\"portrait_url\":\"" + longUrl + "\",\"site_path\":\"javascript:alert(1)\"}"));
        assertEquals("", s.portraitUrl(), "endereço pela metade é pior que nenhum");
        assertEquals("", s.sitePath(), "só http(s) ou caminho do site");
        FichaSheet ok = FichaSheet.parse(json("{\"portrait_url\":\"/uploads/p.png\",\"site_path\":\"/profile/rpg/ficha/9\"}"));
        assertEquals("/uploads/p.png", ok.portraitUrl());
        assertEquals("/profile/rpg/ficha/9", ok.sitePath());
        assertEquals("", FichaSheet.parse(json("{\"site_path\":\"//evil.example/x\"}")).sitePath(), "// é outro host");
        assertEquals("", FichaSheet.parse(json("{\"site_path\":\"/a b\"}")).sitePath());
    }

    @Test
    void parseObjectNeverThrows() {
        assertNull(Json.parseObject(null));
        assertNull(Json.parseObject(""));
        assertNull(Json.parseObject("{quebrado"));
        assertNull(Json.parseObject("[1,2]"), "lista não é objeto");
        assertNull(Json.parseObject("x".repeat(Json.MAX_DOCUMENT + 1)), "texto grande demais nem é lido");
        String deep = "{\"a\":".repeat(40_000) + "1" + "}".repeat(40_000);
        assertDoesNotThrow(() -> Json.parseObject(deep), "aninhamento fundo não derruba a thread");
        assertDoesNotThrow(() -> FichaSheet.parse(Json.parseObject(deep)));
    }

    @Test
    void recordsAreImmutableAndNullSafe() {
        FichaSheet s = FichaSheet.parse(json(GoFixtures.SHEET_WITH_BUILD));
        assertThrows(UnsupportedOperationException.class, () -> s.skills().add(null));
        assertThrows(UnsupportedOperationException.class, () -> s.resources().conditions().add("x"));

        List<String> mutable = new ArrayList<>(List.of("medo"));
        FichaSheet.Resources r = new FichaSheet.Resources(true, 1, 1, 1, 1, 0, null, 0, mutable, 0, 0, 0, false);
        mutable.add("caido");
        assertEquals(List.of("medo"), r.conditions(), "cópia defensiva: quem criou não mexe depois");
        assertEquals("", r.vital());

        FichaSheet empty = new FichaSheet(0, null, 0, null, null, null, null, 0, 0, null, null, false, false, false,
                null, null, null, null, null, null, null);
        assertEquals("", empty.name());
        assertEquals(FichaSheet.Lore.EMPTY, empty.lore());
        assertEquals(FichaSheet.Resources.EMPTY, empty.resources());
        assertTrue(empty.skillsByAttribute().isEmpty());
        assertDoesNotThrow(() -> Json.write(empty.toJson()));

        RollResult roll = new RollResult(null, null, null, null, 0, 0, 0, 0, null, false, null);
        assertEquals("normal", roll.mode());
        assertTrue(roll.dice().isEmpty());
        assertEquals("", Texts.outcome(roll));
        assertEquals("0", Texts.breakdown(roll, false, false));
    }

    @Test
    void rollCardViewIsLeastPrivilegeWhenUnknown() {
        JsonObject roll = json(GoFixtures.ROLL_ADVANTAGE);
        JsonObject o = new JsonObject();
        o.addProperty("view", "admin");
        o.add("roll", roll);
        RollCard card = RollCard.parse(o);
        assertNotNull(card);
        assertEquals(RollCard.PUBLIC, card.view());
        assertFalse(card.gm());
        assertFalse(card.own());
    }

    @Test
    void candidatesFilterUnknownTypesDuplicatesAndKeepUuidProfiles() {
        String uuid = "0f8fad5b-d9cb-469f-a165-70867728950e";
        List<Candidate> list = Candidate.parseList(json("""
                {"c": [
                  {"subject_type":"sheet","subject_id":41,"kind":"sheet","name":"Lyra","title":"","portrait_url":"","staff_only":false,"bound_profiles":[]},
                  {"subject_type":"npc","subject_id":7,"kind":"npc","name":"Bram","title":"Ferreiro","portrait_url":"","staff_only":false,
                   "bound_profiles":["%s"]},
                  {"subject_type":"npc","subject_id":7,"kind":"npc","name":"Bram de novo","staff_only":true,"bound_profiles":[]},
                  {"subject_type":"campaign","subject_id":3,"name":"tipo desconhecido"},
                  {"subject_type":"npc","subject_id":-5,"name":"id negativo"}
                ]}""".formatted(uuid)).getAsJsonArray("c"));
        assertEquals(2, list.size());
        assertEquals("Bram", list.get(1).name(), "a primeira ocorrência vale");
        assertEquals(List.of(uuid), list.get(1).boundProfiles(), "perfil sem nome vem como UUID inteiro, não cortado");
        assertTrue(Candidate.parseList(null).isEmpty());
        Candidate noKind = new Candidate("npc", 1, null, null, null, null, false, null);
        assertEquals("npc", noKind.kind(), "kind vazio = subject_type, como o Go faz");
        assertTrue(noKind.boundProfiles().isEmpty());
    }

    @Test
    void goRollsReadCorrectly() {
        RollResult adv = RollResult.parse(json(GoFixtures.ROLL_ADVANTAGE));
        assertNotNull(adv);
        assertEquals("Destreza + Furtividade", adv.label());
        assertFalse(adv.dice().get(0).dropped(), "dropped omitido = mantido");
        assertTrue(adv.dice().get(1).dropped());
        assertEquals("com vantagem", Texts.modeLabel(adv.mode()));
        assertEquals("nat 17 + 6 = 23 · CD 15", Texts.breakdown(adv, false, false));
        assertEquals("SUCESSO", Texts.outcome(adv));
        assertEquals("17 · (4)", Texts.diceList(adv, 6));

        RollResult neg = RollResult.parse(json(GoFixtures.ROLL_NEGATIVE));
        assertNotNull(neg);
        assertNull(neg.success());
        assertEquals("nat 1 − 2 = −1", Texts.breakdown(neg, false, false), "o mesmo sinal no bônus e no total");
        assertEquals("FALHA CRÍTICA", Texts.outcome(neg));
        assertEquals("1d20-2", Texts.d20Formula(neg.bonus()), "a fórmula fica igual à do motor");

        RollResult free = RollResult.parse(json(GoFixtures.ROLL_FREE_D20));
        assertNotNull(free);
        assertEquals("", Texts.outcome(free), "rolagem livre não tem crítico, mesmo com 20 no dado");
        assertEquals("1d20+2 = 22", Texts.breakdown(free, false, false));
        RollResult localFree = new RollResult("free", "1d20", List.of(new RollResult.Die(20, 20, false)), "normal", 20, 0, 20, 0,
                null, false, "1d20");
        assertEquals("", Texts.outcome(localFree), "nem quando o natural vem preenchido (ficha de teste)");
    }
}
