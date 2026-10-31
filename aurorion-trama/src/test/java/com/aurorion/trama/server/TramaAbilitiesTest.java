package com.aurorion.trama.server;

import com.aurorion.core.character.CharacterAbilities.Item;
import com.aurorion.core.character.CharacterAbilities.State;
import com.aurorion.trama.progression.ProgressRules;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TramaAbilitiesTest {
    @Test void formationProgressFollowsTheSlowerRequirement() {
        // Ponto 6 com o padrão: 80 XP e 15 min. Metade do XP, tempo inteiro: a barra fica na metade.
        Item half = TramaAbilities.formation(5, 40, 15 * 60, 2, 80, 20, 15, 4);
        assertEquals(State.PROGRESS, half.state());
        assertEquals(50, half.progress());
        assertEquals("5/45", half.level());
        assertTrue(half.detail().contains("faltam 40 XP"), half.detail());
        assertTrue(half.detail().contains("e 0 min"), half.detail());
        assertTrue(half.detail().startsWith("2 pontos para gastar."), half.detail());

        Item time = TramaAbilities.formation(5, 500, 0, 1, 80, 20, 15, 4);
        assertEquals(0, time.progress());
        assertTrue(time.detail().contains("15 min"), time.detail());
        assertTrue(time.detail().startsWith("1 ponto para gastar."));
    }

    @Test void fullFormationIsActiveWithoutBar() {
        Item full = TramaAbilities.formation(ProgressRules.MAX_POINTS, 0, 0, 0, 80, 20, 15, 4);
        assertEquals(State.ACTIVE, full.state());
        assertEquals(-1, full.progress());
    }

    @Test void threadsAreSummedAndSpecialNodesListed() {
        // C01 Fio da Carne (VIT +1), C02 Fio da Guarda (ARM +1), I00 Origem Ignivar (Início).
        List<Item> items = TramaAbilities.nodes(List.of("C01", "C02", "I00", "XYZ"));
        assertEquals(2, items.size());
        assertEquals("Origem Ignivar", items.get(0).name());
        assertEquals("Início", items.get(0).level());
        Item threads = items.get(1);
        assertEquals("2 Fios", threads.name());
        assertEquals("VIT +1, ARM +1.", threads.detail());
        assertTrue(TramaAbilities.nodes(List.of()).isEmpty());
    }
}
