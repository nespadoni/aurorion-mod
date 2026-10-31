package com.aurorion.ficha.server;

import com.aurorion.core.character.CharacterAbilities.Group;
import com.aurorion.core.character.CharacterAbilities.Item;
import com.aurorion.core.character.CharacterAbilities.State;
import com.aurorion.ficha.network.FichaPayloads;
import com.aurorion.ficha.sheet.Habilidades;
import com.aurorion.ficha.sheet.Json;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Core → View sem Minecraft: ordem, estados, tetos e o corte no pacote. */
class HabilidadesColetaTest {
    @Test void convertKeepsOrderStatesAndKeys() {
        List<Group> groups = new ArrayList<>();
        groups.add(new Group("personagem", "Personagem", "#FF0000", 90,
                List.of(Item.of("Vidas", "", "3 restantes", State.ACTIVE, "minecraft:totem_of_undying"))));
        groups.add(new Group("vazio", "Vazio", "", 1, List.of()));
        groups.add(new Group("magias", "Magias", "#9B7BFF", 10, List.of(
                Item.of("Fireball", "Throw", "", State.UNLOCKED, "irons_spellbooks:scroll")
                        .withKeys("spell.irons_spellbooks.fireball", "spell.irons_spellbooks.fireball.guide"),
                // Barra só em "progresso": um ACTIVE com progresso não desenha barra.
                Item.of("Ligada", "", "", State.ACTIVE, "").withProgress(70),
                Item.of("Formação", "", "5/45", State.PROGRESS, "").withProgress(30))));
        Habilidades h = HabilidadesColeta.convert(groups);
        assertEquals(List.of("magias", "personagem"), h.grupos().stream().map(Habilidades.Grupo::id).toList());
        List<Habilidades.Item> itens = h.grupos().get(0).itens();
        assertEquals("liberada", itens.get(0).estado());
        assertEquals("spell.irons_spellbooks.fireball", itens.get(0).nomeChave());
        assertEquals(-1, itens.get(1).progresso());
        assertEquals("ativa", itens.get(1).estado());
        assertEquals("progresso", itens.get(2).estado());
        assertEquals(30, itens.get(2).progresso());
        assertEquals("#FF0000", h.grupos().get(1).cor());
        // E o que vai pela rede volta igual na tela.
        JsonObject view = new JsonObject();
        view.add("habilidades", h.toJson());
        assertEquals(h, Habilidades.parse(view, key -> null));
    }

    @Test void neverMoreThanTheScreenAccepts() {
        List<Group> groups = new ArrayList<>();
        for (int g = 0; g < 12; g++) {
            List<Item> items = new ArrayList<>();
            for (int i = 0; i < 64; i++) items.add(Item.of("n" + i, "d", "", State.UNLOCKED, ""));
            groups.add(new Group("g" + g, "G", "", g, items));
        }
        Habilidades h = HabilidadesColeta.convert(groups);
        assertEquals(Habilidades.MAX_GRUPOS, h.grupos().size());
        assertTrue(HabilidadesColeta.convert(List.of()).isEmpty());
    }

    @Test void fitDropsAbilityGroupsBeforeEmptyingTheView() {
        List<Group> groups = new ArrayList<>();
        for (int g = 0; g < 8; g++) {
            List<Item> items = new ArrayList<>();
            for (int i = 0; i < 64; i++) items.add(Item.of("n".repeat(64), "d".repeat(400), "", State.UNLOCKED, ""));
            groups.add(new Group("g" + g, "G", "", g, items));
        }
        JsonObject json = new JsonObject();
        json.addProperty("profile_name", "Alguem");
        JsonArray array = HabilidadesColeta.convert(groups).toJson();
        json.add("habilidades", array);
        assertTrue(Json.write(json).length() > FichaPayloads.VIEW_JSON, "o teste precisa passar do teto");
        String text = FichaServer.fit(json, FichaPayloads.VIEW_JSON);
        assertTrue(text.length() <= FichaPayloads.VIEW_JSON);
        JsonObject back = Json.parseObject(text);
        assertEquals("Alguem", back.get("profile_name").getAsString());
        int kept = back.getAsJsonArray("habilidades").size();
        assertTrue(kept > 0 && kept < 8, "sai do último grupo, não tudo: " + kept);
        assertEquals("g0", back.getAsJsonArray("habilidades").get(0).getAsJsonObject().get("id").getAsString());
    }
}
