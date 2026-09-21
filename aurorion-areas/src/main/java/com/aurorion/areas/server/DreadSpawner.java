package com.aurorion.areas.server;

import com.aurorion.areas.profile.AmbientProfile;
import com.aurorion.core.level.SafeSpot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.event.EventHooks;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * O que nasce em volta de quem fica tempo demais num ambiente.
 *
 * <h2>Isto nao e spawn natural</h2>
 *
 * <p>Nao mexe no {@code NaturalSpawner}, nao ocupa cota de mob do chunk e nao depende de luz nem de
 * bioma: sao criaturas colocadas perto de <b>um jogador especifico</b>, porque ele esta ali ha tempo
 * demais. A regra {@code monstros} da area continua mandando — se ela nega spawn hostil, o
 * {@code EntityJoinLevelEvent} do proprio modulo recusa estas junto com as naturais, sem nenhuma
 * checagem a mais aqui. Os multiplicadores de vida e dano da area tambem pegam nelas pelo mesmo
 * caminho.
 *
 * <h2>Como isto nao vira um exercito</h2>
 *
 * <p>Tres travas independentes, porque uma so falharia sozinha:
 *
 * <ul>
 *   <li><b>Teto por jogador</b> ({@code max_nearby}): contamos as <em>nossas</em> criaturas vivas em
 *       volta dele antes de criar qualquer outra. Ficar duas horas na floresta nao acumula nada.</li>
 *   <li><b>Nenhuma e persistente</b>: o despawn do vanilla leva embora tudo o que ficar longe de
 *       jogador. A floresta volta ao normal sozinha quando ninguem esta nela.</li>
 *   <li><b>Nunca gera chunk</b>: um ponto fora de chunk carregado e descartado em vez de forcar a
 *       geracao dele na thread do servidor (SDD §2).</li>
 * </ul>
 *
 * <h2>Custo</h2>
 *
 * <p>Uma tentativa a cada 20–45 segundos <b>por jogador que esta dentro de um ambiente com spawns
 * declarados</b>, nunca por tick e nunca para quem esta fora. A unica parte cara — varrer as
 * entidades em volta — acontece nessa tentativa, e so depois de o relogio estourar.
 */
final class DreadSpawner {
    /**
     * Marca as criaturas nascidas daqui. Serve para dois fins: contar so as nossas no teto por
     * jogador, e deixar rastro para a staff entender de onde veio aquele bicho.
     */
    static final String TAG = "aurorion_areas_dread";
    /** Tentativas de achar um lugar antes de desistir desta criatura. */
    private static final int PLACEMENT_TRIES = 8;

    private DreadSpawner() {
    }

    static void spawnWave(ServerPlayer player, AmbientProfile.Spawns spawns, float dread) {
        ServerLevel level = player.serverLevel();
        // Em peaceful o jogo apaga hostis no tick seguinte; criar seria so gastar.
        if (level.getDifficulty() == Difficulty.PEACEFUL || spawns.entries().isEmpty()) return;

        int wanted = spawns.countAt(dread);
        if (wanted <= 0 || spawns.maxNearby() <= 0) return;

        // A varredura vem antes de qualquer criacao: com o teto cheio, a tentativa inteira custa uma
        // consulta e nada mais.
        //
        // O raio e o mesmo do anel de spawn, e nao um multiplo dele: a consulta varre secoes de 16
        // blocos, entao dobrar o raio octuplica o trabalho (216 secoes em vez de 27, com os 22 blocos
        // padrao). O teto mede o quao cheio esta o redor do jogador — uma criatura que ja andou para
        // fora do anel nao esta mais apertando ninguem, e liberar a vaga dela e o comportamento certo,
        // nao um efeito colateral.
        List<Mob> ours = level.getEntitiesOfClass(Mob.class,
                player.getBoundingBox().inflate(spawns.maxDistance()),
                mob -> mob.getTags().contains(TAG));
        int room = spawns.maxNearby() - ours.size();
        if (room <= 0) return;

        for (int i = 0; i < Math.min(wanted, room); i++) {
            spawnOne(player, level, spawns, dread);
        }
    }

    private static void spawnOne(ServerPlayer player, ServerLevel level, AmbientProfile.Spawns spawns, float dread) {
        RandomSource random = player.getRandom();
        AmbientProfile.Spawns.Entry entry = pick(spawns, dread, random);
        if (entry == null) return;

        // Id de mod que nao esta instalado e ignorado em silencio, igual aos sons: a mesma lista de
        // criaturas serve a um pack com Born in Chaos e a um sem ele.
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(entry.id()).orElse(null);
        if (type == null) return;

        BlockPos spot = findSpot(level, player, spawns, entry.flying(), random);
        if (spot == null) return;

        if (!(type.create(level) instanceof Mob mob)) return;
        mob.moveTo(spot.getX() + .5, spot.getY(), spot.getZ() + .5, random.nextFloat() * 360F, 0);
        // noCollision depois do moveTo e antes de entrar no mundo: uma criatura grande pode nao caber
        // num vao que serviria para um jogador, e nascer dentro da parede a mataria por sufocamento.
        if (!level.noCollision(mob)) { mob.discard(); return; }

        // EventHooks, e nao mob.finalizeSpawn direto: aquele metodo e @OverrideOnly no NeoForge, e
        // chamar por fora do hook pularia o FinalizeSpawnEvent — que e onde os outros mods do pack
        // ajustam equipamento, variante e dificuldade da criatura que acabou de nascer.
        EventHooks.finalizeMobSpawn(mob, level, level.getCurrentDifficultyAt(spot), MobSpawnType.EVENT, null);
        mob.addTag(TAG);
        // Nunca setPersistenceRequired: e o despawn do vanilla que limpa a floresta depois.
        if (dread >= spawns.huntFrom()) mob.setTarget(player);
        level.addFreshEntity(mob);
    }

    /**
     * Sorteio por peso, apenas entre o que o medo atual ja libera.
     *
     * <p>O peso total e somado a cada leva em vez de guardado: a lista muda de tamanho conforme o
     * medo sobe, e ela tem no maximo 32 entradas — somar 32 inteiros uma vez a cada 20 segundos nao
     * paga um cache que precisaria ser invalidado no {@code /reload}.
     */
    @Nullable
    private static AmbientProfile.Spawns.Entry pick(AmbientProfile.Spawns spawns, float dread, RandomSource random) {
        int total = 0;
        for (AmbientProfile.Spawns.Entry entry : spawns.entries()) {
            if (dread >= entry.from()) total += entry.weight();
        }
        if (total <= 0) return null;

        int roll = random.nextInt(total);
        for (AmbientProfile.Spawns.Entry entry : spawns.entries()) {
            if (dread < entry.from()) continue;
            roll -= entry.weight();
            if (roll < 0) return entry;
        }
        return null;
    }

    /**
     * Um lugar em volta do jogador, num anel: perto o bastante para ele encontrar, longe o bastante
     * para nao aparecer do nada na frente dele.
     *
     * @return o ponto, ou {@code null} se nenhuma das tentativas serviu — desistir e melhor que
     * insistir num lugar ruim, porque a proxima leva vem em segundos.
     */
    @Nullable
    private static BlockPos findSpot(ServerLevel level, ServerPlayer player, AmbientProfile.Spawns spawns,
                                     boolean flying, RandomSource random) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int attempt = 0; attempt < PLACEMENT_TRIES; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double distance = spawns.minDistance()
                    + random.nextDouble() * (spawns.maxDistance() - spawns.minDistance());
            int x = Mth.floor(player.getX() + Math.cos(angle) * distance);
            int z = Mth.floor(player.getZ() + Math.sin(angle) * distance);

            // getChunkNow, e nao getChunk: ler um chunk descarregado o GERARIA na thread do servidor.
            LevelChunk chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
            if (chunk == null) continue;

            if (flying) {
                int y = player.getBlockY() + 6 + random.nextInt(10);
                cursor.set(x, y, z);
                if (level.isEmptyBlock(cursor) && level.isEmptyBlock(cursor.above())) return cursor.immutable();
                continue;
            }
            // Mesmo criterio de "da para ficar de pe aqui" que o respawn usa (SafeSpot, no core):
            // chao de verdade, corpo livre e nenhum fluido.
            for (int y = player.getBlockY() + 4; y >= player.getBlockY() - 6; y--) {
                if (level.isOutsideBuildHeight(y)) continue;
                if (SafeSpot.fits(level, chunk, cursor.set(x, y, z))) return cursor.immutable();
            }
        }
        return null;
    }

    /** Uma criatura desta floresta? Usado so para diagnostico da staff. */
    static boolean isOurs(Entity entity) {
        return entity.getTags().contains(TAG);
    }
}
