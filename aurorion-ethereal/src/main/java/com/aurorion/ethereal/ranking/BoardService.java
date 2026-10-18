package com.aurorion.ethereal.ranking;

import com.aurorion.core.character.CharacterData;
import com.aurorion.ethereal.block.entity.AeonicProjectorBlockEntity;
import com.aurorion.ethereal.compat.HouseLivesBridge;
import com.aurorion.ethereal.house.HouseCatalog;
import com.aurorion.ethereal.house.HouseData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Mantem somente os projetores cujos chunks estao carregados. Nenhuma varredura de mundo, nenhum tick.
 *
 * <p>Esta e a diferenca de fundo para o projetor do mod de referencia, que tinha um {@code serverTick}
 * por bloco comparando um contador de geracao a cada segundo: um projetor que ninguem estava olhando
 * custava tick igual, e o custo crescia com a quantidade de projetores construidos no mundo. Aqui o
 * gatilho e o evento que mudou o dado — morte, duelo, missao, ajuste de ponto, vinculacao a uma casa
 * — e o custo e O(projetores carregados daquele modo).
 *
 * <p>O evento so marca o modo como sujo; {@link #tick} recalcula no maximo uma vez por segundo, cada
 * modo uma vez so, e entrega as mesmas linhas a todos os projetores daquele modo. Uma rajada de
 * mudancas de saldo (compra em lote, salario de todo mundo) vira um recalculo, e nao um por saldo.
 */
public final class BoardService {
    /**
     * Identidade e nao {@code equals}: dois {@code BlockEntity} de posicoes diferentes sao objetos
     * diferentes mesmo que configurados igual, e o que queremos rastrear e a instancia carregada.
     */
    private static final Set<AeonicProjectorBlockEntity> LOADED =
            Collections.newSetFromMap(new IdentityHashMap<>());

    /** Intervalo minimo entre dois recalculos dos modos sujos: 1 s. */
    private static final int FLUSH_INTERVAL_TICKS = 20;

    /** Modos com dado novo esperando o proximo {@link #tick}. Thread do servidor apenas. */
    private static final EnumSet<BoardMode> DIRTY = EnumSet.noneOf(BoardMode.class);

    private static int lastFlushTick = -FLUSH_INTERVAL_TICKS;

    private BoardService() {
    }

    public static void loaded(AeonicProjectorBlockEntity projector) {
        LOADED.add(projector);
        refresh(projector);
    }

    public static void unloaded(AeonicProjectorBlockEntity projector) {
        LOADED.remove(projector);
    }

    public static void clear() {
        LOADED.clear();
        DIRTY.clear();
        lastFlushTick = -FLUSH_INTERVAL_TICKS;
    }

    /** Recalcula um projetor so, na hora (acabou de carregar ou de ser configurado). */
    public static void refresh(AeonicProjectorBlockEntity projector) {
        if (projector.isRemoved() || !(projector.getLevel() instanceof ServerLevel level)) {
            return;
        }
        projector.setRenderedLines(lines(level.getServer(), projector.mode()));
    }

    /** As linhas de um modo. Dependem so do servidor e do modo, nunca do projetor. */
    private static List<BoardLine> lines(MinecraftServer server, BoardMode mode) {
        if (mode == BoardMode.RICHEST_HOUSES || mode == BoardMode.RICHEST_PLAYERS) {
            return EconomyBoardBridge.lines(server, mode, AeonicProjectorBlockEntity.MAX_LINES);
        }

        if (mode == BoardMode.LIVES) {
            return lifeLines(server, AeonicProjectorBlockEntity.MAX_LINES);
        }

        List<RankedEntry> ranking = RankingData.get(server).top(
                mode, AeonicProjectorBlockEntity.MAX_LINES, HouseData.get(server), HouseCatalog.all(),
                characterNames(server));

        List<BoardLine> lines = new ArrayList<>(ranking.size());
        for (int i = 0; i < ranking.size(); i++) {
            RankedEntry entry = ranking.get(i);
            lines.add(new BoardLine(mode.line(i + 1, entry.label(), entry.value()), entry.color()));
        }
        return lines;
    }

    /**
     * Marca os modos dados para recalculo. Os projetores carregados que mostram algum deles recebem
     * as linhas novas no proximo {@link #tick} permitido (no maximo 1 s depois).
     */
    public static void refresh(MinecraftServer server, BoardMode... modes) {
        Collections.addAll(DIRTY, modes);
    }

    /**
     * Fim de cada tick do servidor. Sem modo sujo sai na primeira linha; com, recalcula no maximo uma
     * vez a cada {@value #FLUSH_INTERVAL_TICKS} ticks, cada modo uma vez so.
     */
    public static void tick(MinecraftServer server) {
        if (DIRTY.isEmpty()) {
            return;
        }
        int now = server.getTickCount();
        if (now >= lastFlushTick && now - lastFlushTick < FLUSH_INTERVAL_TICKS) {
            return;
        }
        lastFlushTick = now;

        EnumMap<BoardMode, List<BoardLine>> computed = new EnumMap<>(BoardMode.class);
        for (AeonicProjectorBlockEntity projector : LOADED) {
            BoardMode mode = projector.mode();
            if (projector.isRemoved() || !(projector.getLevel() instanceof ServerLevel level)
                    || level.getServer() != server || !DIRTY.contains(mode)) {
                continue;
            }
            List<BoardLine> lines = computed.get(mode);
            if (lines == null) {
                lines = lines(server, mode);
                computed.put(mode, lines);
            }
            projector.setRenderedLines(lines);
        }
        DIRTY.clear();
    }

    /**
     * Chamada sempre que o total de alguma casa pode ter mudado — e isso inclui alguem <b>entrar ou
     * sair</b> de uma casa, nao so ganhar ponto: o total e a soma dos membros.
     */
    public static void refreshHouses(MinecraftServer server) {
        refresh(server, BoardMode.TOP_HOUSES, BoardMode.WORST_HOUSES);
    }

    /** Ponto de jogador mexe nos dois rankings de jogador e, por tabela, no total da casa dele. */
    public static void refreshPoints(MinecraftServer server) {
        refresh(server, BoardMode.TOP_PLAYERS, BoardMode.WORST_PLAYERS,
                BoardMode.TOP_HOUSES, BoardMode.WORST_HOUSES);
    }

    /** Chamada pela ponte opcional do mod de economia, somente quando algum saldo muda. */
    public static void refreshEconomy(MinecraftServer server) {
        refresh(server, BoardMode.RICHEST_HOUSES, BoardMode.RICHEST_PLAYERS);
    }

    /** Chamada pela ponte opcional do mod de vidas, somente quando algum contador muda. */
    public static void refreshLives(MinecraftServer server) {
        refresh(server, BoardMode.LIVES);
    }

    /**
     * O nome de aluno em todo modo de jogador do projetor: o do <b>personagem</b> — o mesmo que o placar
     * de vidas, a economia e o {@code /fakename} (que nasce com ele) mostram —, e nao o nick da conta que
     * o {@code RankingData} grava. Sem personagem nomeado (conta que ainda nao passou pela criacao), fica
     * o nick gravado, para a linha nao sair em branco.
     */
    public static RankingData.Names characterNames(MinecraftServer server) {
        CharacterData characters = CharacterData.get(server);
        return (id, recorded) -> {
            CharacterData.Character character = characters.find(id);
            return character != null && character.named() ? character.fullName() : recorded;
        };
    }

    /** Todo modo que mostra nome de aluno. Nomear ou renomear um personagem refaz estes. */
    public static void refreshPlayerNames(MinecraftServer server) {
        refresh(server, BoardMode.TOP_PLAYERS, BoardMode.WORST_PLAYERS, BoardMode.MISSIONS,
                BoardMode.LIVES, BoardMode.DUEL_WINS, BoardMode.RICHEST_PLAYERS);
    }

    private static List<BoardLine> lifeLines(MinecraftServer server, int limit) {
        List<RankedEntry> ranking = CharacterData.get(server).characters().entrySet().stream()
                .filter(entry -> entry.getValue().named() && !entry.getValue().dead())
                .map(entry -> new RankedEntry(entry.getKey().toString(), entry.getValue().fullName(),
                        HouseLivesBridge.livesOf(server, entry.getKey())))
                .sorted(java.util.Comparator.comparingInt(RankedEntry::value)
                        .thenComparing(RankedEntry::label, String.CASE_INSENSITIVE_ORDER))
                .limit(limit)
                .toList();

        List<BoardLine> lines = new ArrayList<>(ranking.size());
        for (int i = 0; i < ranking.size(); i++) {
            RankedEntry entry = ranking.get(i);
            lines.add(new BoardLine(BoardMode.LIVES.line(i + 1, entry.label(), entry.value()), entry.color()));
        }
        return lines;
    }
}
