package com.aurorion.profissoes;

import com.aurorion.profissoes.server.SessionBook;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class SessionBookTest {
    @Test void onlyTheRecipientCanAcceptAndOnlyOnce() {
        var book = new SessionBook<String>(); var owner = UUID.randomUUID();
        var token = book.put(owner, "repair", 100);
        assertNull(book.take(UUID.randomUUID(), token, 200));
        assertNull(book.take(owner, UUID.randomUUID(), 200));
        assertEquals("repair", book.take(owner, token, 200));
        assertNull(book.take(owner, token, 200));
    }
    @Test void expirationAndReplacementInvalidateOlderScreens() {
        var book = new SessionBook<String>(); var owner = UUID.randomUUID();
        var expired = book.put(owner, "old", 0);
        assertNull(book.take(owner, expired, 30_000));
        var old = book.put(owner, "old", 40_000); var current = book.put(owner, "current", 40_001);
        assertNull(book.take(owner, old, 40_002));
        assertEquals("current", book.take(owner, current, 40_002));
    }
    @Test void logoutRemovesSessionsReferencingTheDepartingPlayer() {
        var book = new SessionBook<UUID>(); var a = UUID.randomUUID(); var b = UUID.randomUUID();
        var token = book.put(a, b, 0);
        book.removeIf((owner, peer) -> owner.equals(b) || peer.equals(b));
        assertNull(book.take(a, token, 1));
    }
}
