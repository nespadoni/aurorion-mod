package com.aurorion.essentials.voice;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Quem pode entrar no grupo de voz de uma ligacao: os dois lados, e mais ninguem. */
class PhoneCallRegistryTest {
    private final UUID call = UUID.fromString("00000000-0000-0000-0000-0000000000ca");

    @Test
    void soOsDoisLadosEntram() {
        PhoneCallRegistry calls = new PhoneCallRegistry();
        calls.register(call, "Alice", "Bob", 0L);
        assertFalse(calls.refuses(call, "alice"));
        assertFalse(calls.refuses(call, "BOB"));
        assertTrue(calls.refuses(call, "Eve"));
        assertTrue(calls.refuses(call, null));
    }

    @Test
    void grupoQueNaoELigacaoNaoEAfetado() {
        PhoneCallRegistry calls = new PhoneCallRegistry();
        assertFalse(calls.isCall(call));
        assertFalse(calls.refuses(call, "Eve"));
    }

    @Test
    void ligacaoDesligadaEEsquecida() {
        PhoneCallRegistry calls = new PhoneCallRegistry();
        calls.register(call, "Alice", "Bob", 0L);
        calls.forget(call);
        assertFalse(calls.isCall(call));
    }

    @Test
    void grupoAbandonadoSaiDepoisDaCarencia() {
        PhoneCallRegistry calls = new PhoneCallRegistry();
        calls.register(call, "Alice", "Bob", 0L);
        calls.prune(10L, 100L, id -> false);
        assertTrue(calls.isCall(call), "dentro da carencia o telefone ainda esta colocando os dois");
        calls.prune(100L, 100L, id -> false);
        assertFalse(calls.isCall(call));
    }

    @Test
    void oRegistroTemTeto() {
        PhoneCallRegistry calls = new PhoneCallRegistry();
        for (int i = 0; i < PhoneCallRegistry.MAX_CALLS + 10; i++) calls.register(UUID.randomUUID(), "a", "b", i);
        assertEquals(PhoneCallRegistry.MAX_CALLS, calls.size());
    }
}
