package com.aurorion.essentials.compat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Qual entrada do Gram/Twitter/marketplace e do personagem que morreu. */
class PhoneSocialResetTest {
    /** Mesmo formato dos registros do telefone: texto em campos, um deles o nick. */
    private record Post(String postId, String playerName, String text) {
    }

    private record Follow(String follower, String followed) {
    }

    @Test
    void normalizesLikeThePhone() {
        assertEquals("steve_42", PhoneSocialReset.normalize(" @Steve_42 "));
        assertEquals("arthur_pendragon", PhoneSocialReset.normalize("Arthur Pendragon"));
    }

    @Test
    void anEntryIsTheCharactersWhenAnyTextFieldIsTheNick() {
        String key = PhoneSocialReset.normalize("Steve_42");
        assertTrue(PhoneSocialReset.mentions(new Post("p1", "steve_42", "oi"), key));
        assertTrue(PhoneSocialReset.mentions(new Follow("alex", "Steve_42"), key));
        assertTrue(PhoneSocialReset.mentions("STEVE_42", key));
    }

    @Test
    void otherPlayersEntriesStay() {
        String key = PhoneSocialReset.normalize("Steve_42");
        assertFalse(PhoneSocialReset.mentions(new Post("p2", "alex", "o Steve_42 sumiu"), key));
        assertFalse(PhoneSocialReset.mentions(new Follow("alex", "morgana"), key));
        assertFalse(PhoneSocialReset.mentions(42L, key));
        assertFalse(PhoneSocialReset.mentions(null, key));
    }
}
