package com.aurorion.magia.passive;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

/**
 * "Em quem estou olhando?" para as passivas de tecla. Passiva nao passa pela mira do Iron's (nao e
 * conjuracao), entao a escolha e feita aqui: o jogador mais alinhado com o olhar, dentro do alcance e a
 * vista.
 *
 * <p>Custo: uma volta em {@code level.players()} — lista que o servidor ja mantem — e no maximo um
 * raycast de linha de visao, so quando alguem aperta a tecla.
 */
public final class PassiveTargets {
    /** Cosseno do angulo maximo entre o olhar e o alvo: ~11 graus. */
    private static final double MIN_ALIGNMENT = 0.98;

    private PassiveTargets() {
    }

    @Nullable
    public static ServerPlayer lookedAt(ServerPlayer viewer, double range, Predicate<ServerPlayer> filter) {
        if (!(viewer.level() instanceof ServerLevel level)) return null;
        Vec3 eye = viewer.getEyePosition();
        Vec3 look = viewer.getLookAngle();
        double rangeSqr = range * range;
        ServerPlayer best = null;
        double bestAlignment = MIN_ALIGNMENT;
        for (ServerPlayer other : level.players()) {
            if (other == viewer || !other.isAlive() || other.isSpectator() || !filter.test(other)) continue;
            Vec3 toward = other.position().add(0, other.getBbHeight() * 0.6, 0).subtract(eye);
            double distanceSqr = toward.lengthSqr();
            if (distanceSqr > rangeSqr || distanceSqr < 1.0E-4) continue;
            // Perto, o corpo ocupa mais do campo de visao: a tolerancia abre um pouco.
            double slack = Math.min(0.06, 0.5 / Math.sqrt(distanceSqr));
            double alignment = toward.normalize().dot(look) + slack;
            if (alignment > bestAlignment) {
                best = other;
                bestAlignment = alignment;
            }
        }
        return best != null && viewer.hasLineOfSight(best) ? best : null;
    }
}
