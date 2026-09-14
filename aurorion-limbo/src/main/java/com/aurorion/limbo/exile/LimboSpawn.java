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
 * <p>Ler o mapa de altura de uma coluna <b>gera o chunk</b> se ele nao existir, na thread do servidor.
 * Por isso o numero de tentativas e baixo e o raio e modesto: isso roda no respawn, que e raro, mas
 * uma busca de cinquenta tentativas num raio de dois mil blocos seria meio segundo de travada no pior
 * momento possivel.
 */
public final class LimboSpawn {
    /** Poucas tentativas de proposito: cada uma pode gerar um chunk. */
    private static final int ATTEMPTS = 8;

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

    /**
     * O topo caminhavel da coluna {@code (x, z)}.
     *
     * <p>{@code MOTION_BLOCKING_NO_LEAVES} e o heightmap certo: ele ignora folhagem, entao numa
     * floresta densa o ponto cai no <b>chao</b> e nao em cima de uma copa de arvore.
     */
    @Nullable
    public static BlockPos surface(ServerLevel level, int x, int z) {
        BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));

        // Comeca dois blocos acima do topo: o heightmap aponta para o primeiro espaco livre, e o
        // scanDown quer uma altura de partida com folga para achar o par chao+vao.
        return SafeSpot.scanDown(level, x, z, top.getY() + 2);
    }
}
