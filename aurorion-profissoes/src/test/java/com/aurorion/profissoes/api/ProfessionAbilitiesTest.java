package com.aurorion.profissoes.api;

import com.aurorion.core.character.CharacterAbilities.Item;
import com.aurorion.core.character.CharacterAbilities.State;
import com.aurorion.profissoes.data.Profession;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProfessionAbilitiesTest {
    @Test void everyProfessionHasAHeaderAndItsPerks() {
        for (Profession p : Profession.values()) {
            List<Item> items = ProfessionAbilities.items(p, true, 3, 10, 3, .5, false);
            assertFalse(items.isEmpty(), p.name());
            if (p == Profession.NONE) {
                assertEquals(State.LOCKED, items.get(0).state());
                assertEquals(1, items.size());
            } else {
                assertEquals(p.label(), items.get(0).name());
                assertEquals(State.ACTIVE, items.get(0).state());
                assertTrue(items.size() >= 2, p.name());
            }
            for (Item item : items) assertFalse(item.icon().isEmpty(), item.name());
        }
    }

    @Test void numbersComeFromTheConfig() {
        List<Item> arcanist = ProfessionAbilities.items(Profession.ARCANIST, true, 4, 7, 3, .5, true);
        assertTrue(arcanist.stream().anyMatch(i -> i.detail().contains("até o nível VII")));
        assertTrue(arcanist.stream().anyMatch(i -> i.detail().contains("teto IV")));
        assertTrue(arcanist.stream().anyMatch(i -> i.name().equals("Apotheosis")));
        assertFalse(ProfessionAbilities.items(Profession.ARCANIST, true, 3, 10, 3, .5, false).stream()
                .anyMatch(i -> i.name().equals("Apotheosis")));
        List<Item> chef = ProfessionAbilities.items(Profession.CHEF, true, 3, 10, 5, .75, false);
        assertTrue(chef.get(1).detail().contains("nível 5"));
        assertTrue(chef.get(1).detail().contains("a 0,75× do ritmo normal"), chef.get(1).detail());
        assertEquals("na metade do ritmo normal", ProfessionAbilities.decay(.5));
        assertEquals("a 1× do ritmo normal", ProfessionAbilities.decay(1.0));
    }

    @Test void disabledModLocksTheProfession() {
        List<Item> items = ProfessionAbilities.items(Profession.SMITH, false, 3, 10, 3, .5, false);
        assertEquals(1, items.size());
        assertEquals(State.LOCKED, items.get(0).state());
    }

    @Test void romanNumerals() {
        assertEquals("III", ProfessionAbilities.roman(3));
        assertEquals("X", ProfessionAbilities.roman(10));
        assertEquals("XLIX", ProfessionAbilities.roman(49));
        assertEquals("CCLV", ProfessionAbilities.roman(255));
        assertEquals("0", ProfessionAbilities.roman(0));
    }
}
