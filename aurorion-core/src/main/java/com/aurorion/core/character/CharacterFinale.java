package com.aurorion.core.character;

import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Encerrar a história de um personagem agora: a morte definitiva, com a cena de fim — a mesma de quando o
 * prazo do Limbo acaba, sem passar pelo Limbo.
 *
 * <p>O {@code aurorion-limbo} responde (é ele que tem a cena e marca o personagem como morto). Quem pede é
 * o duelo até a morte do {@code aurorion-intencao}. <b>Sem o Limbo no pack</b>, {@link #installed()} é
 * {@code false} e quem pergunta não oferece nada que dependa disso — nunca mata pela metade.
 */
public final class CharacterFinale {
    @FunctionalInterface
    public interface Finale {
        /** Marca o personagem atual da conta como morto e mostra a cena de fim (agora, ou no próximo login). */
        void end(MinecraftServer server, UUID account);
    }

    @Nullable
    private static volatile Finale finale;

    private CharacterFinale() {
    }

    public static void provide(Finale value) {
        finale = value;
    }

    public static boolean installed() {
        return finale != null;
    }

    /** @return {@code false} se não há quem encerre (nada aconteceu). */
    public static boolean end(MinecraftServer server, UUID account) {
        Finale current = finale;
        if (current == null) return false;
        current.end(server, account);
        return true;
    }
}
