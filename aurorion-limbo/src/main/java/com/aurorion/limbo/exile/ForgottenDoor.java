package com.aurorion.limbo.exile;

import com.aurorion.core.level.SafeSpot;
import com.aurorion.limbo.config.LimboConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * A saida de quem ninguem foi buscar.
 *
 * <h2>A regra, e por que ela e assim</h2>
 *
 * <p>Nas ultimas horas do prazo, se <b>ninguem tentou</b> resgatar a pessoa, ela passa a poder achar
 * uma saida caminhando. Quanto? Uma distancia sorteada entre um minimo e um maximo — na pratica, uma
 * caminhada longa e sem graca por um lugar sem nada.
 *
 * <p>Isso e proposital ate na forma: sair por aqui e a confirmacao mecanica de que o mundo esqueceu
 * essa pessoa. Ninguem pagou nada, ninguem arriscou nada, ninguem apareceu. A caminhada e o preco, e
 * o registro na auditoria e o que transforma isso numa historia em vez de numa saida barata.
 *
 * <h2>Sorteio uma vez, nao sorteio por tick</h2>
 *
 * <p>A ideia original era "uma certa chance de aparecer em algum momento". Do lado de dentro e
 * exatamente o que acontece — a pessoa nao sabe quanto falta e a Porta surge sem aviso. Mas o
 * servidor faz isso de um jeito bem melhor: sorteia <b>a distancia alvo uma unica vez</b>, quando a
 * janela abre, e depois so compara dois numeros.
 *
 * <ul>
 *   <li>Nao roda gerador aleatorio por tick, por exilado (SDD §2).</li>
 *   <li>Nao existe o caso cruel de andar 3000 blocos e o dado nunca cair. Quem andou o combinado
 *       <b>sempre</b> encontra a saida — a mecanica promete e cumpre.</li>
 *   <li>O sorteio fica gravado, entao relogar no meio da caminhada nao reembaralha nada.</li>
 * </ul>
 *
 * <h2>De onde vem a distancia</h2>
 *
 * <p>Das estatisticas que o vanilla ja mantem: andar, correr e agachado. Medir passo com um listener
 * de tick seria trabalho por jogador por tick para descobrir um numero que o jogo ja tem contado.
 *
 * <p>Voar e nadar ficam de fora de proposito. A ficcao e a caminhada longa; elytra atravessando o
 * Limbo em trinta segundos nao e a mesma coisa e nao deveria pagar o mesmo preco.
 */
public final class ForgottenDoor {
    private static final int FRAME_WIDTH = 1;
    private static final int FRAME_HEIGHT = 3;

    /** A Porta nasce a esta distancia a frente: perto o bastante para ver, longe o bastante para andar ate. */
    private static final int SPAWN_AHEAD = 14;
    private static final int SPAWN_SEARCH_RADIUS = 6;

    /** Raio de ativacao da Porta, em blocos. Generoso: nada aqui deve depender de mira. */
    private static final double ENTER_RADIUS = 1.8D;

    /**
     * Travessia autorizada dentro da chamada sincrona de retorno, para jogador/origem/destino exatos.
     *
     * <p>A Porta e um teleporte de sistema numa dimensao que o {@code aurorion_portais} mantem
     * trancada — e trancada de verdade, sem excecao embutida (SDD §8.4). Em vez de abrir um buraco
     * la, a autorizacao mora aqui e e lida por um listener de prioridade mais baixa, que fala por
     * ultimo. E o mesmo arbitro da SDD §9.2: a regra mais especifica decide, sem que os dois mods
     * precisem se conhecer.
     *
     * <p>O finally de quem viaja limpa a autorizacao mesmo se o evento nunca for disparado.
     */
    @Nullable private static ServerPlayer authorized;
    @Nullable private static ResourceKey<Level> authorizedFrom;
    @Nullable private static ResourceKey<Level> authorizedTo;
    private static int authorizedTick;

    private ForgottenDoor() {
    }

    // --- Caminhada -----------------------------------------------------------------------------

    /** Centimetros caminhados no total da vida do jogador; a janela mede a diferenca, nao o valor. */
    public static long walkedCm(ServerPlayer player) {
        var stats = player.getStats();
        return (long) stats.getValue(Stats.CUSTOM.get(Stats.WALK_ONE_CM))
                + stats.getValue(Stats.CUSTOM.get(Stats.SPRINT_ONE_CM))
                + stats.getValue(Stats.CUSTOM.get(Stats.CROUCH_ONE_CM));
    }

    /** Abre a janela: sorteia o alvo e marca o ponto de partida da contagem. */
    public static int arm(ServerLevel level, ExileRecord record, ServerPlayer player) {
        int min = LimboConfig.DOOR_WALK_MIN.get();
        int max = LimboConfig.walkMax();
        int target = min + (max > min ? level.random.nextInt(max - min + 1) : 0);

        record.arm(target, walkedCm(player));
        return target;
    }

    // --- A Porta -------------------------------------------------------------------------------

    /**
     * Constroi a Porta a frente do jogador.
     *
     * @return onde ela ficou, ou {@code null} se nao havia lugar — nesse caso a proxima varredura
     *         tenta de novo, alguns passos adiante.
     */
    @Nullable
    public static BlockPos reveal(ServerLevel level, ServerPlayer player, ExileRecord record) {
        Direction facing = Direction.fromYRot(player.getYRot());
        BlockPos ahead = player.blockPosition().relative(facing, SPAWN_AHEAD);
        BlockState frame = frameBlock();
        // Busca limitada em chunks ja carregados. Nao gera terreno para procurar uma moldura.
        for (int ring = 0; ring <= SPAWN_SEARCH_RADIUS; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (ring > 0 && Math.abs(dx) != ring && Math.abs(dz) != ring) continue;
                    int x = ahead.getX() + dx, z = ahead.getZ() + dz;
                    var chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
                    if (chunk == null) continue;
                    BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
                    int top = Math.min(level.getMaxBuildHeight() - 4, ahead.getY() + 8);
                    int bottom = Math.max(level.getMinBuildHeight() + 1, ahead.getY() - 16);
                    for (int y = top; y >= bottom; y--) {
                        cursor.set(x, y, z);
                        if (!SafeSpot.fits(level, chunk, cursor)) continue;
                        DoorFrame placement = new DoorFrame(cursor.immutable(), facing,
                                BuiltInRegistries.BLOCK.getKey(frame.getBlock()).toString(),
                                level.dimension().location().toString());
                        if (!canBuild(level, placement)) continue;
                        build(level, placement, frame);
                        record.setDoor(placement.base(), facing, placement.block(), placement.dimension());
                        return placement.base();
                    }
                }
            }
        }
        return null;
    }

    /**
     * Moldura vazada de {@code 3x4}, atravessada pela abertura.
     *
     * <p>A saida e a <b>proximidade</b>, e nao um bloco de portal proprio. Um portal de verdade
     * significaria registrar bloco novo — conteudo que, uma vez colocado num mundo, nunca mais sai do
     * modpack (SDD §6.1). Para uma estrutura que existe por alguns minutos e some quando a pessoa
     * atravessa, e caro demais.
     */
    static boolean canBuild(ServerLevel level, DoorFrame frame) {
        for (int w = -FRAME_WIDTH; w <= FRAME_WIDTH; w++) {
            for (int h = 0; h <= FRAME_HEIGHT; h++) {
                BlockPos pos = frame.position(w, h);
                if (level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos)) return false;
                var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
                if (chunk == null || !chunk.getBlockState(pos).isAir()) return false;
            }
        }
        return true;
    }

    private static void build(ServerLevel level, DoorFrame placement, BlockState frame) {
        for (int w = -FRAME_WIDTH; w <= FRAME_WIDTH; w++) {
            for (int h = 0; h <= FRAME_HEIGHT; h++) {
                if (DoorFrame.edge(w, h)) level.setBlockAndUpdate(placement.position(w, h), frame);
            }
        }
    }

    private static BlockState frameBlock() {
        ResourceLocation id = ResourceLocation.tryParse(LimboConfig.DOOR_FRAME_BLOCK.get());
        if (id == null) {
            return Blocks.CRYING_OBSIDIAN.defaultBlockState();
        }
        return BuiltInRegistries.BLOCK.getOptional(id)
                .orElse(Blocks.CRYING_OBSIDIAN)
                .defaultBlockState();
    }

    /** A pessoa entrou na abertura. */
    public static boolean isInside(ServerPlayer player, BlockPos door) {
        return player.distanceToSqr(door.getX() + 0.5D, door.getY() + 1.0D, door.getZ() + 0.5D)
                <= ENTER_RADIUS * ENTER_RADIUS;
    }

    /** Apaga a moldura depois da travessia: a Porta era daquela pessoa, e ela ja foi. */
    public static void erase(MinecraftServer server, ExileRecord record) {
        if (record.doorPos() == null) return;
        // Save antigo nao guardava orientacao/material. Nao adivinhar e apagar construcao alheia.
        if (record.doorFacing() != null && record.doorBlock() != null && record.doorDimension() != null) {
            DoorFrame frame = new DoorFrame(record.doorPos(), record.doorFacing(), record.doorBlock(), record.doorDimension());
            if (!eraseLoaded(server, frame)) LimboData.get(server).deferDoor(frame);
        }
        record.clearDoor();
    }

    private static boolean eraseLoaded(MinecraftServer server, DoorFrame frame) {
        ResourceLocation dim = ResourceLocation.tryParse(frame.dimension());
        ResourceLocation block = ResourceLocation.tryParse(frame.block());
        if (dim == null || block == null) return true;
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, dim));
        if (level == null) return false;
        Block material = BuiltInRegistries.BLOCK.getOptional(block).orElse(null);
        if (material == null) return true;
        boolean complete = true;
        for (int w = -FRAME_WIDTH; w <= FRAME_WIDTH; w++) {
            for (int h = 0; h <= FRAME_HEIGHT; h++) {
                if (!DoorFrame.edge(w, h)) continue;
                BlockPos pos = frame.position(w, h);
                var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
                if (chunk == null) {
                    complete = false;
                } else if (chunk.getBlockState(pos).is(material)) {
                    level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                }
            }
        }
        return complete;
    }

    /** Uma vez por minuto, so se houver limpeza pendente; nunca forca chunk offline. */
    public static void cleanPending(MinecraftServer server) {
        LimboData data = LimboData.get(server);
        var pending = data.pendingDoors();
        if (pending.isEmpty()) return;
        var iterator = pending.values().iterator();
        while (iterator.hasNext()) {
            if (eraseLoaded(server, iterator.next())) {
                iterator.remove();
                data.setDirty();
            }
        }
    }

    // --- Autorizacao de travessia --------------------------------------------------------------

    public static void authorize(ServerPlayer player, ResourceKey<Level> destination) {
        authorized = player;
        authorizedFrom = player.level().dimension();
        authorizedTo = destination;
        authorizedTick = player.server.getTickCount();
    }

    /** Consulta e limpa: a autorizacao vale para uma travessia, no tick em que foi dada. */
    public static boolean consumeAuthorization(ServerPlayer player, ResourceKey<Level> destination) {
        if (authorized != player || authorizedFrom != player.level().dimension()
                || authorizedTo != destination || authorizedTick != player.server.getTickCount()) return false;
        clear();
        return true;
    }

    /** Limpa a autorizacao ao terminar a chamada, inclusive se a viagem falhar. */
    public static void clear() {
        authorized = null;
        authorizedFrom = null;
        authorizedTo = null;
    }
}
