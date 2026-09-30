package com.aurorion.essentials.client;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A regra da limpeza da agenda depois de um reset de personagem.
 *
 * <p>O que estes testes protegem: o contato do personagem morto tem de sair, mas um contato
 * adicionado depois — do personagem novo da mesma conta — nao pode sumir na entrada seguinte. Por
 * isso cada reset e tratado uma vez so.
 */
class RetiredContactsTest {
    private final UUID bellaMorta = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private final UUID proprioMorto = UUID.fromString("00000000-0000-0000-0000-0000000000a1");

    private Map<UUID, String> retired() {
        Map<UUID, String> retired = new LinkedHashMap<>();
        retired.put(bellaMorta, "BellaNoob");
        return retired;
    }

    @Test
    void contatoDoPersonagemMortoSai() {
        RetiredContacts plan = RetiredContacts.plan(retired(), Set.of(), "Carlin");
        assertTrue(plan.forgets("BellaNoob"));
        assertTrue(plan.forgets("bellanoob"), "o telefone guarda o nick como veio; a comparacao ignora caixa");
        assertFalse(plan.forgets("Carlin"));
        assertFalse(plan.wipeAll());
    }

    @Test
    void resetJaTratadoNaoApagaOContatoDoPersonagemNovo() {
        RetiredContacts plan = RetiredContacts.plan(retired(), Set.of(bellaMorta), "Carlin");
        assertTrue(plan.isEmpty());
        assertFalse(plan.forgets("BellaNoob"));
    }

    @Test
    void resetDaPropriaContaEsvaziaAAgenda() {
        Map<UUID, String> retired = retired();
        retired.put(proprioMorto, "Carlin");
        RetiredContacts plan = RetiredContacts.plan(retired, Set.of(), "carlin");
        assertTrue(plan.wipeAll());
        assertTrue(plan.forgets("QualquerUm"));
        assertEquals(Set.of(bellaMorta, proprioMorto), plan.newlyProcessed());
    }
}
