package com.aurorion.vidas.lives;

import com.aurorion.core.level.SafeSpot;
import com.aurorion.vidas.AurorionVidas;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Onde o exilado aparece.
 *
 * <p>O caminho bom e o admin marcar o lugar com {@code /vidas exilio aqui}, depois de construir a
 * chegada. O que esta aqui e a rede de seguranca para o primeiro exilio de um servidor onde ninguem
 * marcou nada — e o resultado e <b>gravado</b>, para que todos os exilados de sempre cheguem no
 * mesmo lugar em vez de espalhados. Um ponto de chegada compartilhado tambem e melhor de jogo: a
 * "recepcao do inferno" vira um lugar reconhecivel.
 *
 * <p>A varredura em si e {@link SafeSpot}, no {@code aurorion-core} — a mesma que o
 * {@code aurorion-portais} usa para o respawn de quem morre em dimensao trancada. Aqui so ficam a
 * politica (onde procurar, e o que fazer quando nao acha) e a gravacao do resultado.
 *
 * <p>Roda no maximo uma vez na vida do mundo, então pode ser cara à vontade: carrega alguns chunks
 * perto do spawn da dimensao de exilio e acaba.
 */
public final class ExileSpot {
    /** Colunas varridas ao redor do spawn da dimensao: raio 3 = 7x7, dentro de poucos chunks. */
    private static final int SPIRAL_RADIUS = 3;

    /**
     * Altura em que a varredura comeca, descendo.
     *
     * <p>96 nao e arbitrario: no Nether fica <em>abaixo</em> da cavidade do teto de bedrock (que
     * seria tecnicamente segura e pessima de jogo) e acima do oceano de lava, ou seja, na area onde
     * se joga de verdade. No overworld e no End fica acima do relevo, então a primeira parada
     * descendo e a superficie.
     */
    private static final int SCAN_TOP = 96;

    private ExileSpot() {
    }

    /** O ponto marcado para esta dimensao; se nao houver, procura um, grava e devolve. */
    public static BlockPos resolve(ServerLevel level, LivesData data) {
        BlockPos marked = data.exilePosIn(level.dimension());
        if (marked != null) {
            return marked;
        }

        BlockPos found = SafeSpot.aroundColumn(level, level.getSharedSpawnPos(), SPIRAL_RADIUS, SCAN_TOP);
        if (found == null) {
            found = level.getSharedSpawnPos();
            AurorionVidas.LOGGER.error(
                    "Nao achei ponto seguro de exilio em '{}' perto de {}. Usando o spawn da dimensao, que pode ser "
                            + "dentro de bloco ou de lava. Marque um lugar decente com /vidas exilio aqui.",
                    level.dimension().location(), level.getSharedSpawnPos());
        } else {
            AurorionVidas.LOGGER.warn(
                    "Ponto de exilio escolhido automaticamente em '{}' {}. Ele foi gravado e todos os exilados vao "
                            + "chegar ai. Se voce tem um lugar melhor construido, use /vidas exilio aqui.",
                    level.dimension().location(), found);
        }

        data.setExile(level.dimension(), found);
        return found;
    }
}
