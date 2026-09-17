package com.aurorion.economia.api;

import com.aurorion.core.character.CharacterData;
import com.aurorion.economia.money.Money;
import com.aurorion.economia.server.HouseTreasury;
import com.aurorion.economia.server.WalletData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** API somente de leitura para Projetor Aeonico e telas administrativas. */
public final class EconomyRankingApi {
    public record PlayerRow(String label, long fragments, String formatted) { }
    public record HouseRow(String id, long fragments, String formatted) { }

    private EconomyRankingApi() { }

    public static List<PlayerRow> richestPlayers(MinecraftServer server, int requestedLimit) {
        int limit = Math.max(1, Math.min(requestedLimit, 100));
        CharacterData characters = CharacterData.get(server);
        List<PlayerRow> rows = new ArrayList<>();
        WalletData.get(server).playerBalances().forEach((account, balance) -> {
            if (balance <= 0) return;
            rows.add(new PlayerRow(label(server, characters, account), balance, Money.format(balance)));
        });
        rows.sort(Comparator.comparingLong(PlayerRow::fragments).reversed()
                .thenComparing(PlayerRow::label, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(rows.size() > limit ? rows.subList(0, limit) : rows);
    }

    /** IDs chegam do catalogo do Ethereal para Casas com saldo zero tambem aparecerem. */
    public static List<HouseRow> richestHouses(MinecraftServer server, List<String> houseIds,
                                                int requestedLimit) {
        int limit = Math.max(1, Math.min(requestedLimit, 100));
        List<HouseRow> rows = new ArrayList<>(houseIds.size());
        for (String raw : houseIds) {
            ResourceLocation id = ResourceLocation.tryParse(raw);
            if (id == null) continue;
            long balance = HouseTreasury.balance(server, id);
            rows.add(new HouseRow(id.toString(), balance, Money.format(balance)));
        }
        rows.sort(Comparator.comparingLong(HouseRow::fragments).reversed().thenComparing(HouseRow::id));
        return List.copyOf(rows.size() > limit ? rows.subList(0, limit) : rows);
    }

    private static String label(MinecraftServer server, CharacterData characters, UUID account) {
        CharacterData.Character character = characters.find(account);
        if (character != null && character.named()) return character.fullName();
        var online = server.getPlayerList().getPlayer(account);
        if (online != null) return online.getDisplayName().getString();
        return account.toString().substring(0, 8);
    }
}
