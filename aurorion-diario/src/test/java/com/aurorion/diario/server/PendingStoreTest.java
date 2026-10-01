package com.aurorion.diario.server;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PendingStoreTest {
    private final List<String> warnings = new ArrayList<>();

    private static PendingStore.Pending pending(String key, String profile, long entry, String title, long at) {
        return new PendingStore.Pending(key, "op-" + key + at, entry, 3, title, "", "{\"document_version\":1,\"blocks\":[]}",
                profile, "char", "Lyra", at, false);
    }

    private PendingStore store(Path file) {
        return new PendingStore(file, Runnable::run, warnings::add);
    }

    @Test
    void survivesRestartAndKeepsOnlyLatestOfASession() throws Exception {
        Path file = Files.createTempDirectory("pend").resolve("aurorion_diario").resolve("pendentes.json");
        PendingStore first = store(file);
        assertTrue(first.put(pending("k1", "p1", 10, "versão 1", 1)));
        assertTrue(first.put(pending("k1", "p1", 10, "versão 2", 2)));
        PendingStore restarted = store(file);
        assertEquals(1, restarted.size());
        assertEquals("versão 2", restarted.get("k1").orElseThrow().title());
        restarted.remove("k1");
        assertEquals(0, store(file).size());
    }

    @Test
    void conflictsWaitForTheAuthor() throws Exception {
        PendingStore s = store(Files.createTempDirectory("pend").resolve("p.json"));
        s.put(pending("a", "p1", 10, "x", 1));
        s.put(pending("b", "p1", 11, "y", 2));
        s.markConflict("a");
        assertEquals(List.of("b"), s.retryable(10).stream().map(PendingStore.Pending::draftKey).toList());
        assertTrue(s.forEntry("p1", 10).orElseThrow().conflict());
        assertFalse(s.forEntry("p2", 10).isPresent(), "outro perfil não enxerga a cópia");
        assertFalse(s.forEntry("p1", 0).isPresent(), "entrada nova não tem id para abrir");
    }

    @Test
    void refusesInsteadOfGrowingForever() throws Exception {
        PendingStore s = store(Files.createTempDirectory("pend").resolve("p.json"));
        for (int i = 0; i < PendingStore.MAX_PER_PROFILE; i++) assertTrue(s.put(pending("k" + i, "p1", i + 1, "t", i)));
        assertFalse(s.put(pending("extra", "p1", 999, "t", 999)), "perfil no teto");
        assertTrue(s.put(pending("k0", "p1", 1, "substitui", 1000)), "substituir a mesma sessão continua permitido");
        assertTrue(s.put(pending("outro", "p2", 1, "t", 1)), "outro perfil não é afetado");
    }

    @Test
    void corruptFileIsReportedNotFatal() throws Exception {
        Path file = Files.createTempDirectory("pend").resolve("p.json");
        Files.writeString(file, "{isto não é json");
        PendingStore s = store(file);
        assertEquals(0, s.size());
        assertEquals(1, warnings.size());
    }
}
