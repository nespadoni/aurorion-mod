package com.aurorion.ficha.sheet;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** O campo {@code habilidades} da View: ida e volta, tetos, campo torto e tradução pela chave. */
class HabilidadesTest {
    private static final java.util.function.Function<String, String> NONE = key -> null;

    private static JsonObject view(JsonArray habilidades) {
        JsonObject view = new JsonObject();
        view.add("habilidades", habilidades);
        return view;
    }

    @Test void roundTripKeepsEveryField() {
        Habilidades h = new Habilidades(List.of(new Habilidades.Grupo("magias", "Magias", "#9B7BFF", List.of(
                new Habilidades.Item("Fireball", "Throw fire", "", "liberada", -1, "irons_spellbooks:scroll",
                        "spell.irons_spellbooks.fireball", "spell.irons_spellbooks.fireball.guide"),
                new Habilidades.Item("Formação", "Próximo ponto…", "5/45", "progresso", 40, "minecraft:amethyst_shard")))));
        Habilidades back = Habilidades.parse(view(h.toJson()), NONE);
        assertEquals(h, back);
        assertEquals(2, back.total());
        assertFalse(back.isEmpty());
        // Sem progresso, o campo nem vai; sem chave, também não.
        JsonObject plain = h.toJson().get(0).getAsJsonObject().getAsJsonArray("itens").get(0).getAsJsonObject();
        assertFalse(plain.has("progresso"));
        JsonObject bar = h.toJson().get(0).getAsJsonObject().getAsJsonArray("itens").get(1).getAsJsonObject();
        assertEquals(40, bar.get("progresso").getAsInt());
        assertFalse(bar.has("nome_chave"));
    }

    @Test void translatedNameWinsWhenTheClientKnowsTheKey() {
        JsonObject item = new JsonObject();
        item.addProperty("nome", "Fireball");
        item.addProperty("nome_chave", "spell.irons_spellbooks.fireball");
        item.addProperty("detalhe", "Throw fire");
        item.addProperty("detalhe_chave", "desconhecida");
        JsonArray itens = new JsonArray();
        itens.add(item);
        JsonObject grupo = new JsonObject();
        grupo.addProperty("id", "magias");
        grupo.add("itens", itens);
        JsonArray array = new JsonArray();
        array.add(grupo);
        Map<String, String> pt = Map.of("spell.irons_spellbooks.fireball", "Bola de §cFogo");
        Habilidades.Item parsed = Habilidades.parse(view(array), pt::get).grupos().get(0).itens().get(0);
        assertEquals("Bola de Fogo", parsed.nome(), "traduzido e sem código de cor");
        assertEquals("Throw fire", parsed.detalhe(), "chave desconhecida: fica o texto do servidor");
        // Sem Minecraft carregado, o tradutor padrão não lança: fica o texto do servidor.
        assertEquals("Fireball", Habilidades.parse(view(array)).grupos().get(0).itens().get(0).nome());
    }

    @Test void crookedInputNeverThrowsAndRespectsLimits() {
        assertSame(Habilidades.EMPTY, Habilidades.parse(null));
        assertSame(Habilidades.EMPTY, Habilidades.parse(new JsonObject()));
        JsonObject wrongType = new JsonObject();
        wrongType.addProperty("habilidades", "x");
        assertSame(Habilidades.EMPTY, Habilidades.parse(wrongType));

        JsonArray groups = new JsonArray();
        JsonObject empty = new JsonObject();
        empty.addProperty("id", "vazio");
        groups.add(empty); // grupo sem itens some e não gasta vaga
        for (int g = 0; g < 20; g++) {
            JsonArray itens = new JsonArray();
            for (int i = 0; i < 100; i++) {
                JsonObject item = new JsonObject();
                item.addProperty("nome", "x".repeat(200));
                item.addProperty("detalhe", "y".repeat(1000));
                item.addProperty("estado", "inventado");
                item.addProperty("progresso", 900);
                itens.add(item);
            }
            JsonObject grupo = new JsonObject();
            grupo.addProperty("id", "g" + g);
            grupo.addProperty("cor", "roxo!!");
            grupo.add("itens", itens);
            groups.add(grupo);
        }
        groups.add("não é objeto");
        Habilidades h = Habilidades.parse(view(groups), NONE);
        assertEquals(Habilidades.MAX_GRUPOS, h.grupos().size());
        assertEquals("g0", h.grupos().get(0).id());
        Habilidades.Grupo first = h.grupos().get(0);
        assertEquals("", first.cor());
        assertEquals(Habilidades.MAX_ITENS, first.itens().size());
        Habilidades.Item item = first.itens().get(0);
        assertEquals(64, item.nome().length());
        assertEquals(400, item.detalhe().codePointCount(0, item.detalhe().length()));
        assertTrue(item.detalhe().endsWith("…"));
        assertEquals("liberada", item.estado());
        assertEquals(100, item.progresso());
    }
}
