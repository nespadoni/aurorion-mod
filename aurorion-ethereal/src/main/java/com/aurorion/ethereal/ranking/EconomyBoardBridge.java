package com.aurorion.ethereal.ranking;

import com.aurorion.ethereal.AurorionEthereal;
import com.aurorion.ethereal.house.House;
import com.aurorion.ethereal.house.HouseCatalog;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** Leitura opcional da economia sem tornar um dos dois mods obrigatorio para o outro. */
final class EconomyBoardBridge {
    private static boolean resolved;
    private static boolean failed;
    private static Method richestPlayers;
    private static Method richestHouses;
    private static Method playerLabel;
    private static Method playerFormatted;
    private static Method houseId;
    private static Method houseFormatted;

    private EconomyBoardBridge() { }

    static List<BoardLine> lines(MinecraftServer server, BoardMode mode, int limit) {
        if (!resolve() || failed) return List.of();
        try {
            return mode == BoardMode.RICHEST_HOUSES
                    ? houseLines(server, limit)
                    : playerLines(server, limit);
        } catch (ReflectiveOperationException | RuntimeException error) {
            failed = true;
            AurorionEthereal.LOGGER.warn("Nao foi possivel ler o ranking do mod de economia.", error);
            return List.of();
        }
    }

    private static List<BoardLine> playerLines(MinecraftServer server, int limit)
            throws ReflectiveOperationException {
        List<?> rows = (List<?>)richestPlayers.invoke(null, server, limit);
        List<BoardLine> lines = new ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            Object row = rows.get(i);
            String label = (String)playerLabel.invoke(row);
            String amount = (String)playerFormatted.invoke(row);
            lines.add(new BoardLine(Component.translatable(
                    "aurorion_ethereal.board.line.richest_players", i + 1, label, amount),
                    RankedEntry.DEFAULT_COLOR));
        }
        return List.copyOf(lines);
    }

    private static List<BoardLine> houseLines(MinecraftServer server, int limit)
            throws ReflectiveOperationException {
        List<String> ids = HouseCatalog.all().stream().map(house -> house.id().toString()).toList();
        List<?> rows = (List<?>)richestHouses.invoke(null, server, ids, limit);
        List<BoardLine> lines = new ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            Object row = rows.get(i);
            ResourceLocation id = ResourceLocation.tryParse((String)houseId.invoke(row));
            House house = id == null ? null : HouseCatalog.get(id);
            String label = house == null ? String.valueOf(id) : house.name().getString();
            int color = house == null ? RankedEntry.DEFAULT_COLOR : house.argb();
            String amount = (String)houseFormatted.invoke(row);
            lines.add(new BoardLine(Component.translatable(
                    "aurorion_ethereal.board.line.richest_houses", i + 1, label, amount), color));
        }
        return List.copyOf(lines);
    }

    private static synchronized boolean resolve() {
        if (resolved) return richestPlayers != null;
        resolved = true;
        try {
            Class<?> api = Class.forName("com.aurorion.economia.api.EconomyRankingApi");
            Class<?> playerRow = Class.forName("com.aurorion.economia.api.EconomyRankingApi$PlayerRow");
            Class<?> houseRow = Class.forName("com.aurorion.economia.api.EconomyRankingApi$HouseRow");
            richestPlayers = api.getMethod("richestPlayers", MinecraftServer.class, int.class);
            richestHouses = api.getMethod("richestHouses", MinecraftServer.class, List.class, int.class);
            playerLabel = playerRow.getMethod("label");
            playerFormatted = playerRow.getMethod("formatted");
            houseId = houseRow.getMethod("id");
            houseFormatted = houseRow.getMethod("formatted");
        } catch (ClassNotFoundException ignored) {
            richestPlayers = null;
        } catch (ReflectiveOperationException error) {
            AurorionEthereal.LOGGER.warn("Economia presente, mas sem API de ranking compativel.", error);
            richestPlayers = null;
        }
        return richestPlayers != null;
    }
}
