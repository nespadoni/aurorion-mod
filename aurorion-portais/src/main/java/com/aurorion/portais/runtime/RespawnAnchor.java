package com.aurorion.portais.runtime;

import com.aurorion.core.level.SafeSpot;
import com.aurorion.portais.line.Station;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Onde alguem renasce quando morre trancado do lado de dentro.
 *
 * <p>A ordem de preferencia e deliberada, da melhor para a menos ruim:
 *
 * <ol>
 *   <li><b>A estacao de desembarque da linha</b> ({@code arrival} no datapack). E a unica opcao que
 *       o servidor sabe que e boa, porque quem construiu a estacao garantiu que e. Vale a pena
 *       declarar sempre.</li>
 *   <li><b>Um vao seguro na coluna onde a pessoa morreu.</b> Chega perto do que o jogador espera
 *       ("acordei onde cai") sem confiar num heightmap que, no Nether, aponta para o teto de
 *       bedrock.</li>
 *   <li><b>Spawn da propria dimensao</b>, resolvido pelo vanilla. Ultimo recurso quando a coluna
 *       inteira e lava ou parede solida — situacao em que renascer no lugar seria um ciclo de
 *       morte.</li>
 * </ol>
 *
 * <p>O passo 2 e {@link SafeSpot}, no {@code aurorion-core}: a mesma checagem serve a chegada do
 * exilio no {@code aurorion-vidas}, e ate esta refatoracao ela existia escrita duas vezes.
 *
 * <p>A busca so roda quando alguem morre dentro de dimensao controlada. Nao ha varredura periodica,
 * nem cache a manter.
 */
public final class RespawnAnchor {
    /** Blocos varridos para cima e para baixo a partir da altura da morte. */
    private static final int VERTICAL_RANGE = 16;

    private RespawnAnchor() {
    }

    /**
     * @return o ponto de respawn dentro de {@code level}, ou {@code null} para deixar o vanilla
     * resolver o spawn da dimensao.
     */
    @Nullable
    public static Vec3 findIn(ServerLevel level, ResourceKey<Level> dimension, BlockPos deathPos) {
        Station arrival = TransitAnnouncer.arrivalFor(dimension);
        if (arrival != null) {
            return arrival.pos().getBottomCenter();
        }

        BlockPos found = SafeSpot.nearestVertical(level, deathPos, VERTICAL_RANGE);
        return found == null ? null : found.getBottomCenter();
    }
}
