package com.aurorion.core.character;

import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Este personagem tem tal nó da árvore de habilidades?
 *
 * <p>O {@code aurorion-trama} responde (pelo estado do Pufferfish que ela já guarda em cache); a magia
 * pergunta pelos nós do Despertar Arcano e da especialidade, sem importar a Trama nem o Pufferfish.
 * Quando um nó muda, quem responde posta {@link SkillTreeChangedEvent} — quem deriva algo do nó
 * recalcula só nesse momento, nunca por tick.
 *
 * <p><b>E se a Trama não estiver no pack?</b> {@link #installed()} responde {@code false}, e quem
 * pergunta volta à regra de antes da árvore (na magia, a liberação por aula). Nunca "ninguém tem nó":
 * isso calaria a magia do servidor inteiro por um jar faltando.
 */
public final class SkillTreeGate {
    /** O que o dono da árvore precisa saber responder. Thread do servidor; só memória. */
    @FunctionalInterface
    public interface Tree {
        boolean has(ServerPlayer player, String node);
    }

    @Nullable
    private static volatile Tree tree;

    private SkillTreeGate() {
    }

    /** Chamado uma vez pelo mod da árvore, na construção dele. */
    public static void provide(Tree value) {
        tree = value;
    }

    public static boolean installed() {
        return tree != null;
    }

    /** @return se o jogador tem o nó; {@code false} também quando não há árvore — confira {@link #installed()}. */
    public static boolean has(ServerPlayer player, String node) {
        Tree current = tree;
        return current != null && current.has(player, node);
    }
}
