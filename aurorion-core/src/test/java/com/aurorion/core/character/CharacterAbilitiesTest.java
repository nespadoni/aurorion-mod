package com.aurorion.core.character;

import com.aurorion.core.character.CharacterAbilities.Group;
import com.aurorion.core.character.CharacterAbilities.Item;
import com.aurorion.core.character.CharacterAbilities.Provider;
import com.aurorion.core.character.CharacterAbilities.State;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

/** O registro sem Minecraft: o jogador é {@code null} e os provedores não o leem. */
class CharacterAbilitiesTest {
    private static Group group(String id, int order, int items) {
        List<Item> list = new ArrayList<>();
        for (int i = 0; i < items; i++) list.add(Item.of("item " + i, "", "", State.UNLOCKED, ""));
        return new Group(id, id, "", order, list);
    }

    private static Map<String, Provider> providers(Object... pairs) {
        Map<String, Provider> map = new TreeMap<>();
        for (int i = 0; i < pairs.length; i += 2) map.put((String) pairs[i], (Provider) pairs[i + 1]);
        return map;
    }

    @Test void groupsComeInOrderAndEmptyOnesAreDropped() {
        Provider magia = p -> List.of(group("passivas", 11, 1), group("magias", 10, 2));
        Provider profissoes = p -> List.of(group("profissao", 20, 1), group("vazio", 5, 0));
        List<Group> out = CharacterAbilities.collect(null, providers("aurorion_profissoes", profissoes, "aurorion_magia", magia));
        assertEquals(List.of("magias", "passivas", "profissao"), out.stream().map(Group::id).toList());
    }

    @Test void aFailingProviderDoesNotTakeTheOthersDown() {
        Provider broken = p -> { throw new IllegalStateException("quebrado"); };
        Provider nulls = p -> null;
        Provider ok = p -> List.of(group("profissao", 20, 1));
        List<Group> out = CharacterAbilities.collect(null, providers("a", broken, "b", nulls, "c", ok));
        assertEquals(1, out.size());
        assertEquals("profissao", out.get(0).id());
    }

    @Test void limitsHoldEvenForCarelessProviders() {
        Provider many = p -> {
            List<Group> list = new ArrayList<>();
            for (int i = 0; i < 20; i++) list.add(group("g" + i, i, 200));
            return list;
        };
        List<Group> out = CharacterAbilities.collect(null, providers("x", many));
        assertEquals(CharacterAbilities.MAX_GROUPS, out.size());
        for (Group g : out) assertEquals(CharacterAbilities.MAX_ITEMS, g.items().size());
    }

    @Test void itemFieldsAreCappedAndNullSafe() {
        Item item = new Item(null, null, "x".repeat(1000), null, null, null, 250, null);
        assertEquals("", item.name());
        assertEquals(400, item.detail().length());
        assertEquals(State.UNLOCKED, item.state());
        assertEquals(100, item.progress());
        assertEquals(CharacterAbilities.NO_PROGRESS, Item.of("a", "", "", State.ACTIVE, "").withProgress(-9).progress());
        // Corte não parte emoji (par substituto) no meio.
        String emoji = "🔥".repeat(70);
        String name = Item.of(emoji, "", "", State.ACTIVE, "").name();
        assertEquals(64, name.codePointCount(0, name.length()));
        assertEquals(128, name.length());
    }

    @Test void groupColorAndIdAreNormalized() {
        assertEquals("#A567F4", new Group("x", "x", "#a567f4", 0, List.of()).color());
        assertEquals("", new Group("x", "x", "roxo", 0, List.of()).color());
        assertEquals("magias", new Group("Magias", "x", null, 0, null).id());
        assertEquals("#00FF00", CharacterAbilities.color(0xFF00FF00));
        assertEquals("ativa", State.ACTIVE.wire());
        assertEquals("bloqueada", State.LOCKED.wire());
    }
}
