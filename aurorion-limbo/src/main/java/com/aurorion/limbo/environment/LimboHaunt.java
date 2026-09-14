package com.aurorion.limbo.environment;

import com.aurorion.limbo.config.LimboConfig;
import com.aurorion.limbo.registry.LimboSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * A assombracao do Limbo: olhos entre as arvores, vultos, sussurros.
 *
 * <h2>Nada disto existe</h2>
 *
 * <p>Nao ha entidade nenhuma. Nao ha mob que nasce, anda e some — <b>sao particulas e som</b>. Isso
 * nao e economia: e o que faz a mecanica funcionar.
 *
 * <ul>
 *   <li>Um mob de verdade apareceria no F3, poderia ser atacado, morto, preso, e viraria mais um
 *       bicho do modpack. Uma vez que o jogador mata um, o medo acaba.</li>
 *   <li>Um mob custa IA, pathfinding e tick. Isto custa um pacote de particula para <b>uma</b>
 *       pessoa.</li>
 *   <li>E o principal: o que nao existe nao pode ser entendido. O jogador nunca vai confirmar o que
 *       viu, porque nao havia o que confirmar.</li>
 * </ul>
 *
 * <h2>Por que sempre fora do campo de visao</h2>
 *
 * <p>Toda manifestacao nasce <b>atras ou ao lado</b> de quem esta olhando, entre 100 e 180 graus da
 * direcao da cabeca. Some por conta propria em um segundo. O efeito e o jogador virar e nao achar
 * nada — e nao ha truque nenhum ali: a particula acabou sozinha, o tempo todo.
 *
 * <p>Mostrar de frente estragaria duas vezes: a pessoa veria que sao particulas, e teria certeza do
 * que viu.
 *
 * <h2>Custo</h2>
 *
 * <p>Roda dentro da varredura de um segundo que ja existe, so para exilado dentro do Limbo — na
 * pratica, zero a poucos jogadores. Cada manifestacao e um punhado de particulas enviadas a
 * <b>um</b> jogador (nunca broadcast) e, as vezes, um som. Nao ha listener de tick novo, nem entidade,
 * nem estado guardado.
 */
public final class LimboHaunt {
    /** Olhos: vermelho sujo, quase sem brilho. Cor de brasa velha, nao de LED. */
    private static final DustParticleOptions EYE =
            new DustParticleOptions(new Vector3f(0.42F, 0.05F, 0.04F), 0.7F);

    private static final int MIN_DISTANCE = 7;
    private static final int MAX_DISTANCE = 17;

    private LimboHaunt() {
    }

    /**
     * Uma chance de acontecer alguma coisa. Chamada uma vez por segundo, por exilado.
     *
     * <p>A raridade e o efeito. Com algo aparecendo a cada dez segundos, vira cenario e o jogador
     * para de olhar; espacado, cada vez que acontece ele nao tem certeza se aconteceu.
     */
    public static void tick(ServerPlayer player, ServerLevel level) {
        int oneIn = LimboConfig.HAUNT_RARITY.get();
        if (oneIn <= 0) return;

        RandomSource random = level.random;
        if (random.nextInt(oneIn) != 0) return;

        Vec3 spot = behind(player, random);
        if (spot == null) return;

        switch (random.nextInt(5)) {
            case 0, 1 -> eyes(player, level, spot, random);
            case 2 -> shape(player, level, spot);
            default -> whisper(player, level, spot, random);
        }
    }

    /**
     * Um ponto atras do jogador, na altura dos olhos, com chao embaixo.
     *
     * <p>Exige chao para o vulto nao nascer no ar no meio de um vale — a checagem e um
     * {@code getBlockState} em chunk que ja esta carregado, porque esta a menos de 17 blocos de um
     * jogador online.
     */
    private static Vec3 behind(ServerPlayer player, RandomSource random) {
        // 100 a 180 graus da direcao da cabeca, para qualquer um dos lados.
        float turn = (100 + random.nextInt(81)) * (random.nextBoolean() ? 1 : -1);
        float yaw = (player.getYRot() + turn) * Mth.DEG_TO_RAD;
        double distance = MIN_DISTANCE + random.nextInt(MAX_DISTANCE - MIN_DISTANCE);

        double x = player.getX() - Mth.sin(yaw) * distance;
        double z = player.getZ() + Mth.cos(yaw) * distance;
        double y = player.getEyeY();

        BlockPos below = BlockPos.containing(x, y - 1.6D, z);
        if (player.level().getBlockState(below).isAir()
                && player.level().getBlockState(below.below()).isAir()) {
            return null;
        }
        return new Vec3(x, y, z);
    }

    /** Dois pontos na altura dos olhos, separados como um par de olhos a essa distancia. */
    private static void eyes(ServerPlayer player, ServerLevel level, Vec3 spot, RandomSource random) {
        float yaw = player.getYRot() * Mth.DEG_TO_RAD;
        // Perpendicular a linha de visao: o par fica lado a lado de quem olha, nao em profundidade.
        double sx = Mth.cos(yaw) * 0.19D;
        double sz = Mth.sin(yaw) * 0.19D;

        level.sendParticles(player, EYE, true, spot.x - sx, spot.y, spot.z - sz, 1, 0, 0, 0, 0);
        level.sendParticles(player, EYE, true, spot.x + sx, spot.y, spot.z + sz, 1, 0, 0, 0, 0);

        // Metade das vezes, sem som nenhum. Silencio e pior.
        if (random.nextBoolean()) {
            level.playSound(null, spot.x, spot.y, spot.z, LimboSounds.AMBIENT_LIMBO.get(),
                    SoundSource.AMBIENT, 0.22F, 0.55F + random.nextFloat() * 0.2F);
        }
    }

    /** Um vulto passando: fumaca alta e estreita, como alguem atravessando entre as arvores. */
    private static void shape(ServerPlayer player, ServerLevel level, Vec3 spot) {
        level.sendParticles(player, ParticleTypes.LARGE_SMOKE, true,
                spot.x, spot.y - 0.6D, spot.z, 14, 0.12D, 0.9D, 0.12D, 0.0D);
        level.sendParticles(player, ParticleTypes.ASH, true,
                spot.x, spot.y - 0.3D, spot.z, 8, 0.3D, 0.6D, 0.3D, 0.0D);
    }

    /** So som, sem nada para ver. O mais barato e o que mais incomoda. */
    private static void whisper(ServerPlayer player, ServerLevel level, Vec3 spot, RandomSource random) {
        level.playSound(null, spot.x, spot.y, spot.z, LimboSounds.AMBIENT_LIMBO.get(),
                SoundSource.AMBIENT, 0.3F, 0.5F + random.nextFloat() * 0.35F);
    }
}
