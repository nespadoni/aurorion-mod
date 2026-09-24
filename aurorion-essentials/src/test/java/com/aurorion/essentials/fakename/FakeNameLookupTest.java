package com.aurorion.essentials.fakename;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A regra de "quem e essa pessoa" que o {@code /deathhistory} usa.
 *
 * <p>O que estes testes protegem: a staff digita o nome que <b>leu no chat</b>. Esse nome esta em
 * disco com os codigos de cor dentro ("&6Bella &lNoob"), e quem digita nao poe cor nenhuma. Se a
 * comparacao fosse literal, nenhuma consulta acharia ninguem — e o comando voltaria a exigir UUID.
 */
class FakeNameLookupTest {
    private final UUID bella = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private final UUID carlin = UUID.fromString("00000000-0000-0000-0000-0000000000c1");

    private Map<UUID, String> names() {
        Map<UUID, String> names = new LinkedHashMap<>();
        names.put(bella, "&6Bella &lNoob");
        names.put(carlin, "Carlin Netin");
        return names;
    }

    @Test
    void oNomeEAchadoSemAsCoresQueEstaoEmDisco() {
        assertEquals(bella, FakeNameLookup.find(names(), "Bella Noob"));
    }

    @Test
    void maiusculaEEspacoDaPontaNaoImportam() {
        assertEquals(bella, FakeNameLookup.find(names(), "  bella noob "));
        assertEquals(carlin, FakeNameLookup.find(names(), "CARLIN NETIN"));
    }

    @Test
    void nomeDesconhecidoNaoDevolveNinguem() {
        assertNull(FakeNameLookup.find(names(), "Fulano"));
        assertNull(FakeNameLookup.find(names(), "   "));
    }

    /** Dado antigo pode ter dois donos no mesmo nome; a resposta tem de ser sempre a mesma. */
    @Test
    void empateSempreCaiNaMesmaConta() {
        Map<UUID, String> duplicated = names();
        duplicated.put(carlin, "&aBella Noob");
        UUID first = FakeNameLookup.find(duplicated, "Bella Noob");
        assertEquals(first, FakeNameLookup.find(duplicated, "bella noob"));
        assertTrue(first.equals(bella) || first.equals(carlin));
    }

    @Test
    void asSugestoesSaoOsNomesLegiveis() {
        assertEquals(java.util.List.of("Bella Noob", "Carlin Netin"),
                java.util.List.copyOf(FakeNameLookup.plainNames(names())));
    }
}
