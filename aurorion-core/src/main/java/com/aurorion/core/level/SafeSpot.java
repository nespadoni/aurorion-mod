package com.aurorion.core.level;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * "Onde da para colocar um jogador sem mata-lo?"
 *
 * <p>Esta classe existe porque a resposta estava escrita <b>duas vezes</b>, em
 * {@code aurorion-portais} (respawn de quem morreu em dimensao trancada) e {@code aurorion-vidas}
 * (chegada do exilio), com um caractere de diferenca. Duas copias de uma checagem de seguranca sao
 * duas chances de consertar so uma quando aparecer o bug.
 *
 * <p>O criterio e o mesmo nos dois usos, e as alternativas obvias sao piores:
 *
 * <ul>
 *   <li><b>Forma de colisao, e nao "e ar"</b>: um degrau ou uma laje sao chao valido, e uma tocha
 *       nao impede o vao.</li>
 *   <li><b>Nada de fluido</b>, nem no chao nem no corpo. Sem isso, lava passaria por "sem colisao" e
 *       o respawn viraria um ciclo de morte.</li>
 *   <li><b>Dentro da borda do mundo</b>. Um ponto fora dela prende o jogador num lugar de onde ele
 *       leva dano continuo e nao consegue voltar.</li>
 *   <li><b>Nunca um heightmap</b>. No Nether ele aponta para o teto de bedrock, que e onde
 *       exatamente ninguem quer acordar.</li>
 * </ul>
 *
 * <h2>Custo</h2>
 *
 * <p>Os metodos so rodam em eventos raros (morte, exilio), nunca por tick. Ainda assim a varredura e
 * feita <b>por chunk</b>, e nao por bloco: {@code level.getBlockState(pos)} refaz a busca do chunk a
 * cada chamada, e uma varredura de 7x7 colunas por ~100 blocos de altura chega a milhares dessas
 * buscas. Pegando o chunk uma vez por coluna, sao dezenas.
 *
 * <p><b>Aviso que importa em modpack pesado:</b> ler um bloco de um chunk nao carregado <em>gera</em>
 * esse chunk, na thread do servidor. Para o respawn isso e inofensivo (o chunk da morte esta
 * carregado), mas uma varredura larga num lugar nunca visitado pode travar o tick. Por isso o raio e
 * pequeno e a busca acontece uma vez so, com o resultado gravado por quem chama.
 */
public final class SafeSpot {
    private SafeSpot() {
    }

    /**
     * Procura para cima e para baixo a partir de {@code origin}, alternando, e devolve o vao mais
     * proximo em qualquer direcao.
     *
     * <p>Alternar importa: procurar so para cima jogaria quem morreu numa caverna para o teto do
     * mundo, e so para baixo enterraria quem morreu numa montanha.
     *
     * @return o ponto, ou {@code null} se nao ha vao dentro do alcance.
     */
    @Nullable
    public static BlockPos nearestVertical(ServerLevel level, BlockPos origin, int range) {
        int minY = level.getMinBuildHeight() + 1;
        int maxY = level.getMaxBuildHeight() - 2;
        if (minY > maxY) return null;

        int x = origin.getX();
        int z = origin.getZ();
        if (!level.getWorldBorder().isWithinBounds(origin)) return null;

        // Uma coluna, um chunk: pego uma vez e reaproveitado em todas as alturas.
        ChunkAccess chunk = level.getChunk(x >> 4, z >> 4);
        int startY = Mth.clamp(origin.getY(), minY, maxY);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int offset = 0; offset <= range; offset++) {
            int above = startY + offset;
            if (above <= maxY && fits(level, chunk, cursor.set(x, above, z))) {
                return cursor.immutable();
            }

            int below = startY - offset;
            if (offset > 0 && below >= minY && fits(level, chunk, cursor.set(x, below, z))) {
                return cursor.immutable();
            }
        }
        return null;
    }

    /**
     * Varre colunas em quadrados concentricos ao redor de {@code center}, descendo a partir de
     * {@code fromY} em cada uma. A coluna mais proxima do centro vence.
     *
     * <p>Descer (em vez de subir) e o que faz a primeira parada ser a superficie, e nao a primeira
     * bolha de ar acima da bedrock.
     *
     * @param fromY altura em que cada coluna comeca a ser varrida, limitada ao teto do mundo.
     * @return o ponto, ou {@code null} se nenhuma coluna do quadrado serviu.
     */
    @Nullable
    public static BlockPos aroundColumn(ServerLevel level, BlockPos center, int radius, int fromY) {
        for (int ring = 0; ring <= radius; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    // So a casca do quadrado: o miolo ja foi visto num anel menor.
                    if (ring > 0 && Math.abs(dx) != ring && Math.abs(dz) != ring) continue;

                    BlockPos found = scanDown(level, center.getX() + dx, center.getZ() + dz, fromY);
                    if (found != null) {
                        return found;
                    }
                }
            }
        }
        return null;
    }

    /** Primeiro vao descendo a coluna {@code (x, z)} a partir de {@code fromY}. */
    @Nullable
    public static BlockPos scanDown(ServerLevel level, int x, int z, int fromY) {
        int top = Math.min(level.getMaxBuildHeight() - 2, fromY);
        int bottom = level.getMinBuildHeight() + 1;
        if (top < bottom) return null;

        // Barato e antes de tudo: fora da borda nem vale carregar o chunk.
        if (!level.getWorldBorder().isWithinBounds(x, z)) return null;

        ChunkAccess chunk = level.getChunk(x >> 4, z >> 4);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int y = top; y >= bottom; y--) {
            if (fits(level, chunk, cursor.set(x, y, z))) {
                return cursor.immutable();
            }
        }
        return null;
    }

    /**
     * Chao em que da para ficar de pe, dois blocos livres para o corpo, e nada de fluido.
     *
     * <p>O {@code chunk} tem que ser o de {@code pos} — quem chama ja o tem em maos, e passa-lo evita
     * uma busca de chunk por bloco lido.
     *
     * <p>O cursor volta na altura em que entrou, para quem chamou poder usa-lo direto.
     */
    public static boolean fits(ServerLevel level, ChunkAccess chunk, BlockPos.MutableBlockPos pos) {
        int y = pos.getY();

        boolean floor = solidFloor(level, chunk, pos.setY(y - 1));
        boolean feet = floor && isClear(level, chunk, pos.setY(y));
        boolean head = feet && isClear(level, chunk, pos.setY(y + 1));

        pos.setY(y);
        return head;
    }

    private static boolean solidFloor(ServerLevel level, ChunkAccess chunk, BlockPos pos) {
        BlockState state = chunk.getBlockState(pos);
        VoxelShape shape = state.getCollisionShape(level, pos);

        // Cercas e muros passam de Y=1 e invadem a caixa do jogador no bloco de cima. "Tem
        // colisao" sozinho nao basta para considera-los piso seguro.
        return !shape.isEmpty()
                && shape.max(Direction.Axis.Y) <= 1.0D
                && !state.is(CoreLevelTags.UNSAFE_TELEPORT)
                && chunk.getFluidState(pos).isEmpty();
    }

    private static boolean isClear(ServerLevel level, ChunkAccess chunk, BlockPos pos) {
        BlockState state = chunk.getBlockState(pos);
        return !state.is(CoreLevelTags.UNSAFE_TELEPORT)
                && state.getCollisionShape(level, pos).isEmpty()
                && chunk.getFluidState(pos).isEmpty();
    }
}
