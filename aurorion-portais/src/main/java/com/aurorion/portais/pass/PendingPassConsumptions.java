package com.aurorion.portais.pass;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Passes autorizados por {@code EntityTravelToDimensionEvent}, mas ainda nao consumidos.
 *
 * <p>O evento de viagem e cancelavel: outro mod pode permitir que o Aurorion valide a viagem e
 * cancela-la logo depois. Por isso o uso so e debitado quando {@code PlayerChangedDimensionEvent}
 * confirma que a troca realmente aconteceu. As entradas sobrevivem apenas ate o fim do tick.</p>
 */
public final class PendingPassConsumptions {
    private record Pending(ResourceKey<Level> from, ResourceKey<Level> to, ResourceKey<Level> passDimension) {
    }

    private static final Map<UUID, Pending> PENDING = new HashMap<>();

    private PendingPassConsumptions() {
    }

    public static void authorize(UUID player, ResourceKey<Level> from, ResourceKey<Level> to,
                                 ResourceKey<Level> passDimension) {
        PENDING.put(player, new Pending(from, to, passDimension));
    }

    /**
     * @return a dimensao cujo passe deve ser consumido, ou {@code null} se esta troca nao corresponde
     *         a autorizacao pendente. A entrada sempre e removida: uma autorizacao vale uma tentativa.
     */
    @Nullable
    public static ResourceKey<Level> complete(UUID player, ResourceKey<Level> from, ResourceKey<Level> to) {
        Pending pending = PENDING.remove(player);
        if (pending == null || !pending.from().equals(from) || !pending.to().equals(to)) {
            return null;
        }
        return pending.passDimension();
    }

    public static void forget(UUID player) {
        PENDING.remove(player);
    }

    /** Chamado no fim de cada tick e ao parar o servidor; viagem cancelada nunca deixa lixo. */
    public static void clear() {
        PENDING.clear();
    }
}
