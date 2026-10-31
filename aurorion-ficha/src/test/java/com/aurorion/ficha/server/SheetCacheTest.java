package com.aurorion.ficha.server;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SheetCacheTest {
    private final AtomicLong now = new AtomicLong(1_000);
    private final SheetCache<String> cache = new SheetCache<>(now::get);
    private final UUID profile = UUID.randomUUID();

    @Test
    void servesWithinTheTtlAndDropsAfter() {
        cache.put(profile, "char-1", "ficha");
        now.addAndGet(20_000);
        assertEquals("ficha", cache.get(profile, "char-1", 20_000));
        now.addAndGet(1);
        assertNull(cache.get(profile, "char-1", 20_000));
        assertEquals(0, cache.size(), "a entrada vencida sai ao ser lida");
    }

    @Test
    void anotherCharacterOnTheSameProfileMisses() {
        cache.put(profile, "char-1", "ficha velha");
        assertNull(cache.get(profile, "char-2", 20_000), "renasceu com outro personagem: não reaproveita");
        cache.put(profile, null, "sem personagem");
        assertEquals("sem personagem", cache.get(profile, null, 20_000));
    }

    @Test
    void ageCountsFromTheLastPut() {
        assertEquals(-1, cache.ageMs(profile), "sem cópia");
        cache.put(profile, "c", "x");
        now.addAndGet(7_000);
        assertEquals(7_000, cache.ageMs(profile));
        cache.put(profile, "c", "y");
        assertEquals(0, cache.ageMs(profile), "renovar zera a idade (conferida de versão em segundo plano)");
        cache.invalidate(profile);
        assertEquals(-1, cache.ageMs(profile));
    }

    @Test
    void capDropsTheOldestEntry() {
        SheetCache<String> small = new SheetCache<>(now::get, 2);
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();
        small.put(a, null, "a");
        small.put(b, null, "b");
        small.put(a, null, "a2"); // renovada: vai para o fim da fila
        small.put(c, null, "c");
        assertEquals(2, small.size());
        assertNull(small.get(b, null, 20_000), "a mais antiga sai primeiro");
        assertEquals("a2", small.get(a, null, 20_000));
        assertEquals("c", small.get(c, null, 20_000));
    }

    @Test
    void invalidateAndDisabledTtl() {
        cache.put(profile, "c", "x");
        assertNull(cache.get(profile, "c", 0), "cacheSegundos = 0 desliga");
        cache.invalidate(profile);
        assertNull(cache.get(profile, "c", 20_000));
        cache.put(profile, "c", "y");
        cache.clear();
        assertEquals(0, cache.size());
    }
}
