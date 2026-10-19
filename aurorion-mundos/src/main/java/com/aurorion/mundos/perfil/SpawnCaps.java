package com.aurorion.mundos.perfil;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobCategory;

/**
 * O teto de spawn natural por dimensao.
 *
 * <h2>Como chega ao vanilla sem varrer nada</h2>
 *
 * <p>O vanilla decide se uma categoria ainda cabe com {@code MobCategory#getMaxInstancesPerChunk()},
 * consultado em dois lugares: o teto global da dimensao ({@code NaturalSpawner.SpawnState}) e o teto
 * local em volta de cada jogador ({@code LocalMobCapCalculator.MobCounts}). Nenhum dos dois sabe em que
 * dimensao esta. O que sabe e {@code ServerChunkCache#tickChunks}, que roda uma vez por tick por
 * dimensao e e o unico chamador do spawn natural.
 *
 * <p>Por isso o mixin do {@code tickChunks} poe aqui, no comeco, os multiplicadores daquela dimensao
 * ({@link #enter}), e os dois mixins de teto os leem ({@link #cap}). No fim do {@code tickChunks},
 * {@link #exit} volta ao neutro — fora dele, quem pergunta o teto (F3, outro mod) ouve o vanilla.
 *
 * <p>Custo: uma consulta de mapa por dimensao por tick, e uma multiplicacao por categoria por chunk que
 * tenta spawnar. Tudo na thread do servidor; nao ha concorrencia.
 */
public final class SpawnCaps {
    private static double monsters = 1;
    private static double creatures = 1;

    private SpawnCaps() {
    }

    public static void enter(ServerLevel level) {
        WorldProfile profile = WorldProfiles.forLevel(level.dimension());
        monsters = profile == null ? 1 : profile.spawn().monsterCap();
        creatures = profile == null ? 1 : profile.spawn().creatureCap();
    }

    public static void exit() {
        monsters = 1;
        creatures = 1;
    }

    public static int cap(MobCategory category, int original) {
        double factor = category == MobCategory.MONSTER ? monsters : category == MobCategory.CREATURE ? creatures : 1;
        return factor == 1 ? original : (int) Math.round(original * factor);
    }
}
