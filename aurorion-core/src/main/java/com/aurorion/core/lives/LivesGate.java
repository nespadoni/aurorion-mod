package com.aurorion.core.lives;

import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Existe um sistema de vidas neste servidor, e quantas restam a um jogador?
 *
 * <p>Contrato no mesmo molde do {@link com.aurorion.core.house.HouseGate}: o {@code aurorion-vidas}
 * responde, e quem pergunta (hoje, a integracao com o site, para dizer "restam 2 vidas" junto da
 * morte) nao importa o mod de vidas.
 *
 * <p><b>E se o mod de vidas nao estiver no pack?</b> {@link #of} responde {@link #UNKNOWN}, e quem
 * pergunta trata isso como "nao ha sistema de vidas" — nunca como "zero vidas", que anunciaria um
 * exilio que nao aconteceu.
 */
public final class LivesGate {
    /** Resposta de quando nao ha sistema de vidas instalado. */
    public static final int UNKNOWN = -1;

    /** O que o dono das vidas precisa saber responder. Consulta pura; aceita jogador offline. */
    @FunctionalInterface
    public interface Lives {
        int of(MinecraftServer server, UUID player);
    }

    @Nullable
    private static volatile Lives lives;

    private LivesGate() {
    }

    /** Chamado uma vez pelo mod de vidas, na construcao dele. */
    public static void provide(Lives value) {
        lives = value;
    }

    public static boolean installed() {
        return lives != null;
    }

    /** @return as vidas restantes, ou {@link #UNKNOWN} se nao ha sistema de vidas. */
    public static int of(MinecraftServer server, UUID player) {
        Lives current = lives;
        return current == null ? UNKNOWN : current.of(server, player);
    }
}
