package com.aurorion.profissoes.npc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** O nome digitado para a etiqueta passa pelo mesmo filtro da bigorna; o pacote pode vir forjado. */
class NpcTagNameTest {
    @Test
    void nomeComumPassaSemEspacosNasPontas() {
        assertEquals("Trovão", NpcService.tagName("  Trovão  "));
    }

    @Test
    void codigoDeCorEControleSaem() {
        // Igual a bigorna: sai o §, a letra do codigo fica como texto comum.
        assertEquals("cRei", NpcService.tagName("§cR\u0007ei"));
    }

    @Test
    void vazioELongoDemaisSaoRecusados() {
        assertThrows(RuntimeException.class, () -> NpcService.tagName("   "));
        assertThrows(RuntimeException.class, () -> NpcService.tagName("§§"));
        assertThrows(RuntimeException.class, () -> NpcService.tagName("x".repeat(51)));
        assertEquals("x".repeat(50), NpcService.tagName("x".repeat(50)));
    }
}
