package com.aurorion.portais.runtime;

import com.aurorion.core.level.SafeSpot;
import com.aurorion.portais.line.Station;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Onde alguem renasce quando morre trancado do lado de dentro.
 *
 * <p>A ordem de preferencia e deliberada, da melhor para a menos ruim:
 *
 * <ol>
 *   <li><b>A estacao de desembarque da linha</b> ({@code arrival} no datapack), <b>conferida</b>. Antes
 *       ela era usada as cegas: uma estacao com o piso quebrado, coberta de lava ou declarada um bloco
 *       acima do lugar virava um ciclo de morte — morrer, renascer ali, morrer de novo. Agora vale se
 *       couber uma pessoa de pe ({@link SafeSpot#fits}); senao, o vao mais proximo na coluna dela e,
 *       por fim, um quadrado pequeno em volta.</li>
 *   <li><b>Um vao seguro na coluna onde a pessoa morreu.</b> Chega perto do que o jogador espera
 *       ("acordei onde cai") sem confiar num heightmap que, no Nether, aponta para o teto de
 *       bedrock.</li>
 *   <li><b>Spawn da propria dimensao</b>, tambem conferido. O vanilla devolve as coordenadas do
 *       spawn do mundo, que numa dimensao com teto caem dentro da pedra ou em cima da bedrock.</li>
 *   <li><b>A estacao de embarque da linha</b>, do lado de fora, conferida. E uma saida, sim — mas
 *       melhor uma saida do que um ciclo de morte, e so acontece quando a dimensao inteira esta sem
 *       lugar para ficar de pe perto dos tres pontos acima.</li>
 *   <li>Nada serviu: {@code null}, e o vanilla decide (cama/ancora ou spawn do overworld).</li>
 * </ol>
 *
 * <p>Nenhum ponto <em>procurado</em> fica acima do teto logico de uma dimensao com teto (o telhado de
 * bedrock do Nether). Uma estacao declarada la em cima de proposito continua valendo.
 *
 * <p>A busca so roda quando alguem morre dentro de dimensao controlada. Nao ha varredura periodica,
 * nem cache a manter.
 */
public final class RespawnAnchor {
    /** Blocos varridos para cima e para baixo a partir da altura da morte. */
    private static final int VERTICAL_RANGE = 16;
    /** Raio do quadrado em volta de uma estacao cujo bloco exato nao serve. */
    private static final int STATION_RADIUS = 4;

    /** Onde renascer: o mundo e o ponto. O mundo pode nao ser o da morte (passo 4). */
    public record Spot(ServerLevel level, Vec3 pos) {
    }

    private RespawnAnchor() {
    }

    /**
     * @return o ponto de respawn, ou {@code null} para deixar o vanilla resolver.
     */
    @Nullable
    public static Spot find(MinecraftServer server, ServerLevel level, ResourceKey<Level> dimension, BlockPos deathPos) {
        Station arrival = TransitAnnouncer.arrivalFor(dimension);
        if (arrival != null) {
            BlockPos found = nearStation(level, arrival.pos());
            if (found != null) return new Spot(level, found.getBottomCenter());
        }

        BlockPos found = belowCeiling(level, SafeSpot.nearestVertical(level, deathPos, VERTICAL_RANGE));
        if (found != null) return new Spot(level, found.getBottomCenter());

        found = belowCeiling(level, SafeSpot.nearestVertical(level, level.getSharedSpawnPos(), VERTICAL_RANGE));
        if (found != null) return new Spot(level, found.getBottomCenter());

        LineClock clock = TransitClock.forDimension(dimension);
        Station boarding = clock == null ? null : clock.line().station().orElse(null);
        ServerLevel outside = boarding == null ? null : server.getLevel(boarding.dimension());
        if (outside != null) {
            found = nearStation(outside, boarding.pos());
            if (found != null) return new Spot(outside, found.getBottomCenter());
        }
        return null;
    }

    /** A estacao exata se couber uma pessoa; senao a coluna dela; senao um quadrado pequeno em volta. */
    @Nullable
    private static BlockPos nearStation(ServerLevel level, BlockPos station) {
        if (!level.getWorldBorder().isWithinBounds(station)) return null;
        ChunkAccess chunk = level.getChunk(station.getX() >> 4, station.getZ() >> 4);
        if (SafeSpot.fits(level, chunk, station.mutable())) return station;
        BlockPos found = belowCeiling(level, SafeSpot.nearestVertical(level, station, VERTICAL_RANGE));
        if (found != null) return found;
        return belowCeiling(level, SafeSpot.aroundColumn(level, station, STATION_RADIUS, station.getY() + 4));
    }

    /** Descarta ponto procurado em cima do teto de bedrock (ou de qualquer dimensao com teto). */
    @Nullable
    private static BlockPos belowCeiling(ServerLevel level, @Nullable BlockPos pos) {
        if (pos == null) return null;
        if (level.dimensionType().hasCeiling() && pos.getY() >= level.getMinBuildHeight() + level.dimensionType().logicalHeight()) {
            return null;
        }
        return pos;
    }
}
