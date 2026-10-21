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

    /** Nomes em portugues passam: acento precomposto, acento combinante que o NFC junta, apostrofo e hifen. */
    @Test
    void acceptsPortugueseNames() {
        assertEquals("João", new CharacterName("João", "D'Ávila-Conceição").firstName());
        assertEquals("Ñandú Ærø", new CharacterName("Ñandú", "Ærø").fullName());
    }

    /** Letra de outro alfabeto que imita a latina, largura cheia e pilha de acentos nao sao nome. */
    @Test
    void refusesHomoglyphsAndStackedMarks() {
        assertThrows(IllegalArgumentException.class, () -> new CharacterName("Аlda", "Verrine")); // A cirilico
        assertThrows(IllegalArgumentException.class, () -> new CharacterName("Αlda", "Verrine")); // Alfa grego
        assertThrows(IllegalArgumentException.class, () -> new CharacterName("Ａlda", "Verrine")); // A largura cheia
        assertThrows(IllegalArgumentException.class, () -> new CharacterName("Alıce", "Verrine")); // i sem ponto
        assertThrows(IllegalArgumentException.class, () -> new CharacterName("Aq̃́da", "Verrine"), "duas marcas na mesma letra");
        assertThrows(IllegalArgumentException.class, () -> new CharacterName("̃Alda", "Verrine"), "marca sem letra");
        assertThrows(IllegalArgumentException.class, () -> new CharacterName("Al͏da", "Verrine"), "marca invisivel");
        assertThrows(IllegalArgumentException.class,
                () -> new CharacterName("q̃q̃q̃", "q̃q̃"), "mais de quatro marcas no nome");
        assertEquals("q̃q̃ q̃q̃", new CharacterName("q̃q̃", "q̃q̃").fullName());
        assertThrows(IllegalArgumentException.class, () -> new CharacterName("Alda--Rosa", "Verrine"));
        assertThrows(IllegalArgumentException.class, () -> new CharacterName("Alda", "Verrine-"));
    }
}
