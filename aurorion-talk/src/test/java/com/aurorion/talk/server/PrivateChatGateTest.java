package com.aurorion.talk.server;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A lista de comandos recusados e digitada a mao num TOML por quem administra o servidor. O que
 * estes testes fixam e que uma entrada escrita do jeito "errado" ainda barra o sussurro — uma
 * proibicao que falha em silencio por causa de uma barra a mais e pior que nao existir.
 */
class PrivateChatGateTest {
    @Test
    void theDefaultAliasesAreThreeDistinctRoots() {
        Set<String> roots = PrivateChatGate.parse(List.of("msg", "tell", "w"));

        assertEquals(Set.of("msg", "tell", "w"), roots);
    }

    /** O jogo nunca ve a barra, mas quem edita o arquivo pensa no comando com ela. */
    @Test
    void slashesAndCapitalsAndSpacesAreTheSameCommand() {
        Set<String> roots = PrivateChatGate.parse(Arrays.asList("/W", "  tell  ", "//msg"));

        assertEquals(Set.of("w", "tell", "msg"), roots);
    }

    @Test
    void blankAndNullEntriesAreDropped() {
        Set<String> roots = PrivateChatGate.parse(Arrays.asList("msg", "", "   ", "/", null));

        assertEquals(Set.of("msg"), roots);
    }

    @Test
    void theRootTypedByThePlayerIsMatchedAsTyped() {
        Set<String> roots = PrivateChatGate.parse(List.of("msg", "tell", "w"));

        assertTrue(roots.contains(PrivateChatGate.normalize("w")), "/w e apelido de /msg no vanilla, "
                + "mas o que chega ao evento e a palavra digitada");
        assertFalse(roots.contains(PrivateChatGate.normalize("say")));
    }
}
