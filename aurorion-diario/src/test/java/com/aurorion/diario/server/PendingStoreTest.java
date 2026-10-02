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

    private static boolean put(PendingStore store, PendingStore.Pending pending) {
        return store.put(pending).join();
    }

    @Test
    void survivesRestartAndKeepsOnlyLatestOfASession() throws Exception {
        Path file = Files.createTempDirectory("pend").resolve("aurorion_diario").resolve("pendentes.json");
        PendingStore first = store(file);
        assertTrue(put(first, pending("k1", "p1", 10, "versão 1", 1)));
        assertTrue(put(first, pending("k1", "p1", 10, "versão 2", 2)));
        PendingStore restarted = store(file);
        assertEquals(1, restarted.size());
        assertEquals("versão 2", restarted.get("p1", "k1").orElseThrow().title());
        restarted.remove("p1", "k1");
        assertEquals(0, store(file).size());
    }

    @Test
    void sameDraftKeyNeverCrossesProfiles() throws Exception {
        PendingStore s = store(Files.createTempDirectory("pend").resolve("p.json"));
        assertTrue(put(s, pending("k", "p1", 10, "de p1", 1)));
        assertTrue(put(s, pending("k", "p2", 20, "de p2", 2)));
        assertEquals("de p1", s.get("p1", "k").orElseThrow().title());
        s.remove("p2", "k");
        assertTrue(s.get("p1", "k").isPresent(), "remover a chave de outro perfil não apaga a minha");
    }

    @Test
    void conflictsWaitForTheAuthor() throws Exception {
        PendingStore s = store(Files.createTempDirectory("pend").resolve("p.json"));
        PendingStore.Pending a = pending("a", "p1", 10, "x", 1);
        put(s, a);
        put(s, pending("b", "p1", 11, "y", 2));
        s.markConflict(a, 10);
        var diagnostics = s.diagnostics();
        assertEquals(2, diagnostics.total());
        assertEquals(1, diagnostics.retryable());
        assertEquals(1, diagnostics.conflicts());
        assertEquals(1, diagnostics.oldestSavedAt());
        assertTrue(diagnostics.readable());
        assertTrue(diagnostics.lastWriteSucceeded());
        assertFalse(diagnostics.toString().contains("Lyra"));
        assertFalse(diagnostics.toString().contains("p1"));
        assertEquals(List.of("b"), s.retryable(10).stream().map(PendingStore.Pending::draftKey).toList());
        assertTrue(s.forEntry("p1", 10).orElseThrow().conflict());
        assertFalse(s.forEntry("p2", 10).isPresent(), "outro perfil não enxerga a cópia");
        assertFalse(s.forEntry("p1", 0).isPresent(), "entrada nova não tem id para abrir");
        s.discardConflicts("p1", 10);
        assertFalse(s.forEntry("p1", 10).isPresent(), "ficar com a versão do site descarta a cópia recusada");
        s.discardConflicts("p1", 11);
        assertTrue(s.forEntry("p1", 11).isPresent(), "cópia ainda na fila nunca é descartada");
    }

    @Test
    void lateAcknowledgementNeverDropsNewerText() throws Exception {
        PendingStore s = store(Files.createTempDirectory("pend").resolve("p.json"));
        PendingStore.Pending sent = pending("k", "p1", 0, "enviada", 1);
        put(s, sent);
        PendingStore.Pending typedMeanwhile = pending("k", "p1", 0, "digitada depois", 2);
        put(s, typedMeanwhile);
        assertFalse(s.acknowledge(sent, 42, 1), "a cópia mais nova continua guardada");
        PendingStore.Pending rebased = s.get("p1", "k").orElseThrow();
        assertEquals("digitada depois", rebased.title());
        assertEquals(42, rebased.entryId(), "segue sobre a entrada criada pela gravação confirmada");
        assertEquals(1, rebased.baseVersion());
        assertTrue(s.acknowledge(rebased, 42, 2));
        assertEquals(0, s.size());
    }

    @Test
    void newerConfirmationDropsOlderStoredCopy() throws Exception {
        PendingStore s = store(Files.createTempDirectory("pend").resolve("p.json"));
        PendingStore.Pending stored = pending("k", "p1", 10, "antiga, em conflito", 1);
        put(s, stored);
        s.markConflict(stored, 10);
        // A pessoa escolheu manter o texto do jogo: a gravação nova foi direto ao site e voltou ok.
        assertTrue(s.acknowledge(pending("k", "p1", 10, "escolhida", 5), 10, 7));
        assertEquals(0, s.size(), "a cópia antiga não pode voltar a ser enviada por cima da escolhida");
    }

    @Test
    void refusesInsteadOfGrowingForever() throws Exception {
        PendingStore s = store(Files.createTempDirectory("pend").resolve("p.json"));
        for (int i = 0; i < PendingStore.MAX_PER_PROFILE; i++) assertTrue(put(s, pending("k" + i, "p1", i + 1, "t", i)));
        assertFalse(put(s, pending("extra", "p1", 999, "t", 999)), "perfil no teto");
        assertTrue(put(s, pending("k0", "p1", 1, "substitui", 1000)), "substituir a mesma sessão continua permitido");
        assertTrue(put(s, pending("outro", "p2", 1, "t", 1)), "outro perfil não é afetado");
    }

    @Test
    void unreadableFileIsPreservedNotOverwritten() throws Exception {
        Path file = Files.createTempDirectory("pend").resolve("p.json");
        Files.writeString(file, "{isto não é json");
        PendingStore s = store(file);
        assertEquals(0, s.size());
        assertEquals(1, warnings.size());
        assertFalse(put(s, pending("k", "p1", 1, "t", 1)), "não confirma o que não conseguiu gravar");
        assertEquals("{isto não é json", Files.readString(file), "o arquivo fica para recuperação");
    }
}
