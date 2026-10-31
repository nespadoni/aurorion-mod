package com.aurorion.ficha.sheet;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Respostas como o backend Go serializa de verdade ({@code internal/rpg/game.go}): mesma ordem de
 * campos do struct, {@code legacy_level} texto, {@code rarity}/{@code idade} inteiros, {@code version}
 * hex de 16, {@code derived} nulo sem build, {@code success} nulo sem CD, {@code dropped} omitido
 * quando falso.
 */
final class GoFixtures {
    private GoFixtures() {
    }

    static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    /** {@code GameSheet} com construção aprovada: os 6 atributos e as 22 perícias na ordem de {@code rules}. */
    static final String SHEET_WITH_BUILD = """
            {"rpg_character_id":1201,"subject_type":"sheet","subject_id":41,"name":"Lyra Vennamor","title":"",
             "portrait_url":"https://aurorion.example/uploads/sheets/41.png","site_path":"/profile/rpg/ficha/1201",
             "soul":3,"legacy_level":"entidade","element":{"id":"solaris","label":"Solaris"},
             "grade":{"value":2,"label":"Marcado Vinculado"},"has_build":true,"progression_pending":false,"pending_review":false,
             "attributes":[{"id":"for","label":"Força","base":2,"effective":2},{"id":"des","label":"Destreza","base":3,"effective":3},
              {"id":"vig","label":"Vigor","base":2,"effective":2},{"id":"sab","label":"Sabedoria","base":2,"effective":3},
              {"id":"int","label":"Intelecto","base":1,"effective":1},{"id":"vit","label":"Vitral","base":2,"effective":3}],
             "skills":[{"id":"atletismo","label":"Atletismo","attribute":"for","value":1,"bonus":3},
              {"id":"corpo_a_corpo","label":"Corpo a Corpo","attribute":"for","value":0,"bonus":2},
              {"id":"acrobacia","label":"Acrobacia","attribute":"des","value":2,"bonus":5},
              {"id":"furtividade","label":"Furtividade","attribute":"des","value":3,"bonus":6},
              {"id":"pontaria","label":"Pontaria","attribute":"des","value":0,"bonus":3},
              {"id":"resistencia","label":"Resistência","attribute":"vig","value":1,"bonus":3},
              {"id":"astros_e_mistos","label":"Astros & Mistos","attribute":"sab","value":2,"bonus":5},
              {"id":"historia","label":"História","attribute":"sab","value":1,"bonus":4},
              {"id":"ciencia","label":"Ciência","attribute":"sab","value":0,"bonus":3},
              {"id":"natureza","label":"Natureza","attribute":"sab","value":2,"bonus":5},
              {"id":"medicina","label":"Medicina","attribute":"sab","value":1,"bonus":4},
              {"id":"criacao","label":"Criação","attribute":"sab","value":0,"bonus":3},
              {"id":"investigacao","label":"Investigação","attribute":"int","value":2,"bonus":3},
              {"id":"linguistica","label":"Linguística","attribute":"int","value":1,"bonus":2},
              {"id":"psicologia","label":"Psicologia","attribute":"int","value":0,"bonus":1},
              {"id":"tecnologia","label":"Tecnologia","attribute":"int","value":0,"bonus":1},
              {"id":"diplomacia","label":"Diplomacia","attribute":"vit","value":1,"bonus":4},
              {"id":"enganacao","label":"Enganação","attribute":"vit","value":0,"bonus":3},
              {"id":"intimidacao","label":"Intimidação","attribute":"vit","value":0,"bonus":3},
              {"id":"procurar","label":"Procurar","attribute":"vit","value":3,"bonus":6},
              {"id":"intencao","label":"Intenção","attribute":"vit","value":1,"bonus":4},
              {"id":"vontade","label":"Vontade","attribute":"vit","value":2,"bonus":5}],
             "derived":{"vida_max":42,"stamina_max":28,"sanidade_max":30,"pe_max":18,"defesa":14,"rd":2,"movimento_m":9,
              "iniciativa_bonus":4,"fortitude":13,"mark_charges":2,"resonance_b":3},
             "resources":{"initialized":true,"vida":31,"stamina":20,"sanidade":17,"pe":9,"temp_hp":5,"vital":"consciente",
              "exhaustion":1,"conditions":["sangramento","medo"],"charges_used":1,"death_successes":0,"death_failures":0,
              "ultimate_available":true},
             "abilities":[{"name":"Lança de Aurora","short_description":"Um feixe de luz.","element":"solaris","rarity":3,
               "ability_level":2,"equipped":true},
              {"name":"Banquete","short_description":"","element":"martírio","rarity":5,"ability_level":1,"equipped":false}],
             "lore":{"idade":27,"origem":"Porto Velho","alinhamento":"","personalidade":"Curiosa","aparencia":""},
             "version":"9f86d081884c7d65"}
            """;

    /** NPC sem construção aprovada (rascunho enviado): listas vazias, {@code derived} e {@code element} nulos. */
    static final String NPC_WITHOUT_BUILD = """
            {"rpg_character_id":3007,"subject_type":"npc","subject_id":7,"name":"Bram Ferrolho","title":"Ferreiro",
             "portrait_url":"","site_path":"/profile/rpg/ficha/3007","soul":0,"legacy_level":"anomalia","element":null,
             "grade":{"value":0,"label":"Sem Marca"},"has_build":false,"progression_pending":false,"pending_review":true,
             "attributes":[],"skills":[],"derived":null,
             "resources":{"initialized":false,"vida":0,"stamina":0,"sanidade":0,"pe":0,"temp_hp":0,"vital":"consciente",
              "exhaustion":0,"conditions":[],"charges_used":0,"death_successes":0,"death_failures":0,"ultimate_available":true},
             "abilities":[],"lore":{"idade":0,"origem":"","alinhamento":"","personalidade":"","aparencia":""},
             "version":"00ff13a9b2c4d5e6"}
            """;

    /** {@code roll} de um teste de perícia com vantagem ({@code flattenGameRoll}): rótulo do motor, d20 descartado. */
    static final String ROLL_ADVANTAGE = """
            {"kind":"skill","label":"Destreza + Furtividade","dice":[{"sides":20,"value":17},{"sides":20,"value":4,"dropped":true}],
             "mode":"vantagem","natural":17,"bonus":6,"total":23,"target":15,"success":true,"critical":false,"formula":"1d20+6"}
            """;

    /** Teste de atributo sem CD com bônus negativo (condição): {@code success} nulo, fórmula {@code %+d}. */
    static final String ROLL_NEGATIVE = """
            {"kind":"attribute","label":"Intelecto","dice":[{"sides":20,"value":1},{"sides":20,"value":14,"dropped":true}],"mode":"desvantagem","natural":1,
             "bonus":-2,"total":-1,"target":0,"success":null,"critical":false,"formula":"1d20-2"}
            """;

    /** Rolagem livre {@code 1d20+2}: natural 0 no Go (não é teste), sem crítico mesmo com o dado em 20. */
    static final String ROLL_FREE_D20 = """
            {"kind":"free","label":"1d20+2","dice":[{"sides":20,"value":20}],"mode":"normal","natural":0,"bonus":2,
             "total":22,"target":0,"success":null,"critical":false,"formula":"1d20+2"}
            """;
}
