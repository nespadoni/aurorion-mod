package com.aurorion.core.client.menu;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** O registro é estático: um teste só, em ordem, para o host de um passo não vazar para outro. */
class CharacterMenuTest {
    private record Fake(String id, int order, boolean shown, boolean broken) implements CharacterMenu.Section {
        @Override
        public String title() {
            return id;
        }

        @Override
        public String icon() {
            return "star";
        }

        @Override
        public boolean visible() {
            if (broken) throw new IllegalStateException("quebrada");
            return shown;
        }

        @Override
        public List<CharacterMenu.Entry> entries() {
            return List.of();
        }
    }

    @Test void registryOrdersFiltersAndFallsBackWithoutHost() {
        CharacterMenu.register(new Fake("t_duelo", 45, true, false));
        CharacterMenu.register(new Fake("t_perfil", 10, true, false));
        CharacterMenu.register(new Fake("t_acoes", 0, false, false));
        CharacterMenu.register(new Fake("t_quebrada", 5, true, true));
        CharacterMenu.register(new Fake("t_bperfil", 10, true, false));

        List<String> ids = CharacterMenu.sections().stream().map(CharacterMenu.Section::id).filter(id -> id.startsWith("t_")).toList();
        // Ordem, desempate pelo id; a escondida e a que lança ficam de fora.
        assertEquals(List.of("t_bperfil", "t_perfil", "t_duelo"), ids);
        assertNotNull(CharacterMenu.section("t_acoes"));

        // Sem host: tudo diz "não" e quem chamou segue o caminho antigo.
        assertFalse(CharacterMenu.installed());
        assertFalse(CharacterMenu.isOpen());
        assertFalse(CharacterMenu.open("t_perfil"));
        Runnable none = () -> { };
        assertFalse(CharacterMenu.prompt(new CharacterMenu.Modal("X", "y", 0, none, null, 0)));
        CharacterMenu.refresh();

        List<String> calls = new ArrayList<>();
        CharacterMenu.provide(new CharacterMenu.Host() {
            @Override
            public void open(String section) {
                calls.add("open:" + section);
            }

            @Override
            public void prompt(CharacterMenu.Modal modal) {
                calls.add("prompt:" + modal.title());
            }

            @Override
            public void refresh() {
                calls.add("refresh");
            }

            @Override
            public boolean isOpen() {
                return true;
            }
        });
        assertTrue(CharacterMenu.installed());
        assertTrue(CharacterMenu.isOpen());
        assertTrue(CharacterMenu.open("t_perfil"));
        assertTrue(CharacterMenu.prompt(new CharacterMenu.Modal("DUELO", "y", 0, none, null, 5)));
        CharacterMenu.refresh();
        assertEquals(List.of("open:t_perfil", "prompt:DUELO", "refresh"), calls);

        CharacterMenu.registerInteraction(new CharacterMenu.Interaction("t_b", 20, p -> null));
        CharacterMenu.registerInteraction(new CharacterMenu.Interaction("t_a", 10, p -> null));
        assertEquals(List.of("t_a", "t_b"), CharacterMenu.interactions().stream().map(CharacterMenu.Interaction::id)
                .filter(id -> id.startsWith("t_")).toList());
    }
}
