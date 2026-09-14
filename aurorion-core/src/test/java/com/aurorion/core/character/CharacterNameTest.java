package com.aurorion.core.character;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CharacterNameTest {
    @Test
    void acceptsAccentsCompoundPartsAndTrimsSpacing() {
        var name = new CharacterName("  Alda  ", "de Verrine");
        assertEquals("Alda", name.firstName());
        assertEquals("Alda de Verrine", name.fullName());
        assertEquals("alda de verrine", name.key());
    }

    /** O indice ignora acento e caixa: "Álda" e "alda" nao podem ser dois personagens diferentes. */
    @Test
    void collapsesAccentAndCaseIntoTheSameKey() {
        assertEquals(new CharacterName("Álda", "Verrine").key(), new CharacterName("alda", "VERRINE").key());
    }

    @Test
    void refusesWhatIsNotAName() {
        assertThrows(IllegalArgumentException.class, () -> new CharacterName("Alda", ""));
        assertThrows(IllegalArgumentException.class, () -> new CharacterName("A", "Verrine"));
        assertThrows(IllegalArgumentException.class, () -> new CharacterName("Alda7", "Verrine"));
        assertThrows(IllegalArgumentException.class, () -> new CharacterName("§cAlda", "Verrine"));
        assertThrows(IllegalArgumentException.class, () -> new CharacterName("Alda\nVerrine", "Verrine"));
        assertThrows(IllegalArgumentException.class,
                () -> new CharacterName("A".repeat(25), "Verrine"));
        assertThrows(IllegalArgumentException.class,
                () -> new CharacterName("A".repeat(24), "B".repeat(24)));
    }
}
