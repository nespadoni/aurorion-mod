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
 */
public final class BoardService {
    /**
     * Identidade e nao {@code equals}: dois {@code BlockEntity} de posicoes diferentes sao objetos
     * diferentes mesmo que configurados igual, e o que queremos rastrear e a instancia carregada.
     */
    private static final Set<AeonicProjectorBlockEntity> LOADED =
            Collections.newSetFromMap(new IdentityHashMap<>());

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
    }

    /** Recalcula um projetor so. */
    public static void refresh(AeonicProjectorBlockEntity projector) {
        if (projector.isRemoved() || !(projector.getLevel() instanceof ServerLevel level)) {
            return;
        }
        MinecraftServer server = level.getServer();
        BoardMode mode = projector.mode();

        if (mode == BoardMode.RICHEST_HOUSES || mode == BoardMode.RICHEST_PLAYERS) {
            projector.setRenderedLines(EconomyBoardBridge.lines(
                    server, mode, AeonicProjectorBlockEntity.MAX_LINES));
            return;
        }

        if (mode == BoardMode.LIVES) {
            projector.setRenderedLines(lifeLines(server, AeonicProjectorBlockEntity.MAX_LINES));
            return;
        }

        List<RankedEntry> ranking = RankingData.get(server).top(
                mode, AeonicProjectorBlockEntity.MAX_LINES, HouseData.get(server), HouseCatalog.all());

        List<BoardLine> lines = new ArrayList<>(ranking.size());
        for (int i = 0; i < ranking.size(); i++) {
            RankedEntry entry = ranking.get(i);
            lines.add(new BoardLine(mode.line(i + 1, entry.label(), entry.value()), entry.color()));
        }
        projector.setRenderedLines(lines);
    }

    /** Recalcula os projetores carregados que mostram algum dos modos dados. */
    public static void refresh(MinecraftServer server, BoardMode... modes) {
        for (AeonicProjectorBlockEntity projector : LOADED) {
            if (!(projector.getLevel() instanceof ServerLevel level) || level.getServer() != server) {
                continue;
            }
            for (BoardMode mode : modes) {
                if (projector.mode() == mode) {
                    refresh(projector);
                    break;
                }
            }
        }
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
