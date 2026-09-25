package com.aurorion.limbo.exile;

import com.aurorion.core.level.SafeSpot;
import com.aurorion.limbo.AurorionLimbo;
import com.aurorion.limbo.config.LimboConfig;
import com.aurorion.vidas.lives.ExileSpot;
import com.aurorion.vidas.lives.LivesData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

/**
 * Onde alguem acorda no Limbo: sempre na superficie, e nunca duas vezes no mesmo lugar.
 *
 * <h2>Por que espalhado</h2>
 *
 * <p>Com um ponto fixo, morrer no Limbo devolvia a pessoa exatamente onde ela ja tinha estado — e
 * como o Limbo agora tem ruinas, livros e uma floresta para atravessar, isso transformava a morte num
 * <em>rebobinar</em>. Espalhando, cada morte custa territorio: voce acorda em outro lugar do mesmo
 * lugar, sem saber para onde andou antes.
 *
 * <p>Tambem resolve um problema de multidao: com 80 jogadores, um ponto unico junta todos os exilados
 * numa pilha. A dispersao faz cada um ter a propria historia de onde acordou.
 *
 * <h2>Por que superficie, e nao "lugar seguro"</h2>
 *
 * <p>O {@code SafeSpot} procura um <b>vao</b>, e num Limbo com cavernas o primeiro vao costuma ser
 * subterraneo. Acordar dentro de uma caverna sem luz, com Darkness permanente e sem saber que existe
 * um ceu, e outra mecanica — e nao e esta. Aqui o ponto de partida e o mapa de altura, e o
 * {@code SafeSpot} so confere se da para ficar de pe ali.
 *
 * <h2>Custo</h2>
 *
 * <p>Consultar o terreno e checar o vao <b>gera o chunk</b> se ele nao existir, na thread do servidor.
 * Por isso o numero de tentativas e baixo e o raio e modesto: isso roda no respawn, que e raro, mas
 * uma busca de cinquenta tentativas num raio de dois mil blocos seria meio segundo de travada no pior
 * momento possivel.
 */
public final class LimboSpawn {
    /** Poucas tentativas de proposito: cada uma pode gerar um chunk. */
    private static final int ATTEMPTS = 8;
    private static final int MAX_SURFACE_OFFSET = 64;

    private LimboSpawn() {
    }

    /**
     * Um ponto de superficie espalhado em volta da ancora do exilio.
     *
     * @return o ponto, ou {@code null} se nenhuma tentativa serviu — quem chama cai para a ancora.
     */
    @Nullable
    public static BlockPos scattered(ServerLevel level) {
        BlockPos anchor = ExileSpot.resolve(level, LivesData.get(level.getServer()));
        int radius = LimboConfig.SPAWN_SCATTER.get();
        if (radius <= 0) return surface(level, anchor.getX(), anchor.getZ());

        RandomSource random = level.random;

        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            int x = anchor.getX() + random.nextIntBetweenInclusive(-radius, radius);
            int z = anchor.getZ() + random.nextIntBetweenInclusive(-radius, radius);

            BlockPos found = surface(level, x, z);
            if (found != null) return found;
        }

        AurorionLimbo.LOGGER.warn("Nenhuma superficie servivel em {} tentativas perto de {}; usando a ancora.",
                ATTEMPTS, anchor);
        return surface(level, anchor.getX(), anchor.getZ());
    }

    /** Procura uma chegada de superficie perto de um ponto, sem usar a altura de quem esta la. */
    @Nullable
    public static BlockPos around(ServerLevel level, BlockPos center, int radius) {
        for (int ring = 0; ring <= radius; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (ring > 0 && Math.abs(dx) != ring && Math.abs(dz) != ring) continue;
                    BlockPos found = surface(level, center.getX() + dx, center.getZ() + dz);
                    if (found != null) return found;
                }
            }
        }
        return null;
    }

    /**
     * O terreno caminhavel da coluna {@code (x, z)}.
     *
     * <p>A altura base do gerador ignora arvores, estruturas e plataformas suspensas. O heightmap do
     * chunk pronto pode apontar para um piso duzentos blocos acima do terreno. Se os dois divergem
     * tanto, a busca comeca no terreno gerado; nos chunks antigos, onde o gerador pode ter mudado,
     * ela continua usando a superficie existente quando as alturas sao proximas.
     */
    @Nullable
    public static BlockPos surface(ServerLevel level, int x, int z) {
        if (!level.getWorldBorder().isWithinBounds(x, z)) return null;
        int topY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        int terrainY = level.getChunkSource().getGenerator().getBaseHeight(x, z,
                Heightmap.Types.OCEAN_FLOOR_WG, level, level.getChunkSource().randomState());
        int fromY = topY - terrainY > MAX_SURFACE_OFFSET ? terrainY + 2 : topY + 2;
        return SafeSpot.scanDown(level, x, z, fromY);
    }
}
