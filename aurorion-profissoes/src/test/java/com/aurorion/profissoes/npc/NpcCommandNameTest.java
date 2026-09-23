package com.aurorion.profissoes.npc;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Comandos de NPC rodam com permissao 4: o nome do jogador nunca pode virar seletor ou sintaxe. */
class NpcCommandNameTest {
    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000042");

    @Test
    void nomeDeContaMojangPassaComoEsta() {
        assertEquals("Fulano_123", NpcService.commandSafeName("Fulano_123", ID));
    }

    @Test
    void seletorViraUuid() {
        assertEquals(ID.toString(), NpcService.commandSafeName("@a", ID));
        assertEquals(ID.toString(), NpcService.commandSafeName("@e[type=player]", ID));
    }

    @Test
    void sintaxeDeComandoViraUuid() {
        assertEquals(ID.toString(), NpcService.commandSafeName("a\"b", ID));
        assertEquals(ID.toString(), NpcService.commandSafeName("{x}", ID));
        assertEquals(ID.toString(), NpcService.commandSafeName("NomeComMaisDeDezesseis", ID));
    }
}
