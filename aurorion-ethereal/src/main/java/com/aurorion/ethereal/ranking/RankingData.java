package com.aurorion.ethereal.ranking;

import com.aurorion.core.data.SavedDataAccess;
import com.aurorion.ethereal.AurorionEthereal;
import com.aurorion.ethereal.house.House;
import com.aurorion.ethereal.house.HouseData;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pontos, mortes, duelos e missoes — de jogadores e de casas.
 *
 * <p><b>A casa nao tem pontuacao propria: ela tem um bonus.</b> O total de uma casa e o bonus dado
 * pela staff mais a soma dos pontos de todos os membros dela. Essa e a diferenca que fez os dois
 * sistemas terem de morar no mesmo mod: no placar antigo, "casa" era um texto livre digitado na GUI,
 * sem nenhuma ligacao com a casa de verdade do jogador — dar ponto a um aluno nao mexia no placar da
 * casa dele, e renomear a casa no datapack deixava a linha do placar orfa.
 *
 * <p>Nada aqui roda por tick. Morte, duelo, missao e ajuste de ponto sao eventos; cada um deles
 * recalcula so os placares carregados que mostram aquele modo ({@link BoardService}).
 */
public final class RankingData extends SavedData {
    private static final String FILE_ID = AurorionEthereal.MOD_ID + "_rankings";
    private static final String KEY_PLAYERS = "Players";
    private static final String KEY_HOUSES = "HouseBonus";

    private static final SavedDataAccess<RankingData> ACCESS =
            new SavedDataAccess<>(FILE_ID, RankingData::new, RankingData::load);

    private final Map<UUID, PlayerRanking> players = new HashMap<>();
    private final Map<ResourceLocation, Integer> houseBonus = new HashMap<>();

    public static RankingData get(MinecraftServer server) {
        return ACCESS.get(server);
    }

    private static RankingData load(CompoundTag root, HolderLookup.Provider registries) {
        RankingData data = new RankingData();

        ListTag playerTags = root.getList(KEY_PLAYERS, Tag.TAG_COMPOUND);
        for (int i = 0; i < playerTags.size(); i++) {
            CompoundTag tag = playerTags.getCompound(i);
            if (!tag.hasUUID("Id")) continue;
            String name = tag.getString("Name");
            data.players.put(tag.getUUID("Id"), new PlayerRanking(
                    name.isBlank() ? "?" : name,
                    tag.getInt("Points"), tag.getInt("Deaths"), tag.getInt("DuelWins"), tag.getInt("Missions")));
        }

        ListTag houseTags = root.getList(KEY_HOUSES, Tag.TAG_COMPOUND);
        for (int i = 0; i < houseTags.size(); i++) {
            CompoundTag tag = houseTags.getCompound(i);
            // Casa que sumiu do datapack mantem o bonus gravado: o dado nao e destruido porque
            // alguem estava editando JSON. So um id que nem parseia mais e descartado.
            ResourceLocation house = ResourceLocation.tryParse(tag.getString("House"));
            if (house != null) data.houseBonus.put(house, tag.getInt("Points"));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag root, HolderLookup.Provider registries) {
        ListTag playerTags = new ListTag();
        players.forEach((id, ranking) -> {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("Id", id);
            tag.putString("Name", ranking.name());
            tag.putInt("Points", ranking.points());
            tag.putInt("Deaths", ranking.deaths());
            tag.putInt("DuelWins", ranking.duelWins());
            tag.putInt("Missions", ranking.missions());
            playerTags.add(tag);
        });
        root.put(KEY_PLAYERS, playerTags);

        ListTag houseTags = new ListTag();
        houseBonus.forEach((house, points) -> {
            CompoundTag tag = new CompoundTag();
            tag.putString("House", house.toString());
            tag.putInt("Points", points);
            houseTags.add(tag);
        });
        root.put(KEY_HOUSES, houseTags);
        return root;
    }

    // --- Escrita -------------------------------------------------------------------------------

    /** Registra a existencia do jogador e mantem o nome exibido em dia. Chamada no login. */
    public void seePlayer(UUID id, String name) {
        players.compute(id, (ignored, ranking) -> {
            if (ranking == null) {
                setDirty();
                return PlayerRanking.of(name);
            }
            if (!ranking.name().equals(name)) {
                setDirty();
                return ranking.withName(name);
            }
            return ranking;
        });
    }

    public void recordDeath(UUID id, String name) {
        players.put(id, ranking(id, name).withDeath());
        setDirty();
    }

    public void recordDuelWin(UUID id, String name) {
        players.put(id, ranking(id, name).withDuelWin());
        setDirty();
    }

    public void recordMission(UUID id, String name) {
        players.put(id, ranking(id, name).withMission());
        setDirty();
    }

    public boolean adjustPlayerPoints(UUID id, String name, int delta) {
        PlayerRanking ranking = ranking(id, name);
        int updated = safeAdd(ranking.points(), delta);
        if (updated == ranking.points() && players.containsKey(id)) {
            return false;
        }
        players.put(id, ranking.withPoints(updated));
        setDirty();
        return true;
    }

    /** Ajuste de quem ja existe; um id desconhecido e recusado em vez de criar linha vazia. */
    public boolean adjustPlayerPoints(UUID id, int delta) {
        PlayerRanking ranking = players.get(id);
        if (ranking == null) {
            return false;
        }
        int updated = safeAdd(ranking.points(), delta);
        if (updated == ranking.points()) {
            return false;
        }
        players.put(id, ranking.withPoints(updated));
        setDirty();
        return true;
    }

    public boolean adjustHouseBonus(ResourceLocation house, int delta) {
        int old = houseBonus.getOrDefault(house, 0);
        int updated = safeAdd(old, delta);
        if (old == updated && houseBonus.containsKey(house)) {
            return false;
        }
        houseBonus.put(house, updated);
        setDirty();
        return true;
    }

    /**
     * Tira o jogador do placar por completo — pontos, mortes, duelos, missoes e o nome exibido.
     *
     * <p>Serve a troca de personagem: o ranking e da historia, nao da conta. Zerar os pontos e
     * deixar a linha no lugar mostraria "0 pontos" com o nome antigo ate a proxima morte.
     */
    public boolean clearPlayer(UUID id) {
        if (players.remove(id) == null) {
            return false;
        }
        setDirty();
        return true;
    }

    public int playerPoints(UUID id) {
        PlayerRanking ranking = players.get(id);
        return ranking == null ? 0 : ranking.points();
    }

    public int houseBonus(ResourceLocation house) {
        return houseBonus.getOrDefault(house, 0);
    }

    // --- Leitura -------------------------------------------------------------------------------

    /**
     * Total de cada casa do catalogo: bonus mais a soma dos pontos dos membros.
     *
     * <p>Uma passada so pelos jogadores, e nao uma por casa: com 80 jogadores e cinco casas a versao
     * ingenua faria 400 leituras para desenhar um placar que o servidor redesenha a cada morte.
     */
    public Map<ResourceLocation, Integer> houseTotals(HouseData membership, List<House> catalog) {
        Map<ResourceLocation, Integer> totals = new HashMap<>(catalog.size());
        for (House house : catalog) {
            totals.put(house.id(), houseBonus.getOrDefault(house.id(), 0));
        }

        players.forEach((id, ranking) -> {
            ResourceLocation house = membership.houseOf(id);
            if (house != null) {
                totals.computeIfPresent(house, (ignored, total) -> safeAdd(total, ranking.points()));
            }
        });
        return totals;
    }

    /** As {@code limit} primeiras linhas do ranking pedido. */
    public List<RankedEntry> top(BoardMode mode, int limit, HouseData membership, List<House> catalog) {
        List<RankedEntry> entries = mode.isHouseMode()
                ? houseEntries(membership, catalog)
                : playerEntries(mode);
        sortAndTrim(entries, limit, mode.isAscending());
        return entries;
    }

    /** Todas as casas, em ordem alfabetica — a lista da GUI de pontuacao, que nao e um ranking. */
    public List<RankedEntry> allHouses(HouseData membership, List<House> catalog) {
        List<RankedEntry> entries = houseEntries(membership, catalog);
        entries.sort(Comparator.comparing(RankedEntry::label, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(entries);
    }

    public List<RankedEntry> allPlayers() {
        List<RankedEntry> entries = new ArrayList<>(players.size());
        players.forEach((id, ranking) ->
                entries.add(new RankedEntry(id.toString(), ranking.name(), ranking.points())));
        entries.sort(Comparator.comparing(RankedEntry::label, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(entries);
    }

    private List<RankedEntry> houseEntries(HouseData membership, List<House> catalog) {
        Map<ResourceLocation, Integer> totals = houseTotals(membership, catalog);
        List<RankedEntry> entries = new ArrayList<>(catalog.size());
        for (House house : catalog) {
            entries.add(new RankedEntry(house.id().toString(), house.name().getString(),
                    totals.getOrDefault(house.id(), 0), house.argb()));
        }
        return entries;
    }

    private List<RankedEntry> playerEntries(BoardMode mode) {
        List<RankedEntry> entries = new ArrayList<>(players.size());
        players.forEach((id, ranking) ->
                entries.add(new RankedEntry(id.toString(), ranking.name(), valueOf(ranking, mode))));
        return entries;
    }

    private static int valueOf(PlayerRanking ranking, BoardMode mode) {
        return switch (mode) {
            case TOP_PLAYERS, WORST_PLAYERS -> ranking.points();
            case DEATHS -> ranking.deaths();
            case DUEL_WINS -> ranking.duelWins();
            case MISSIONS -> ranking.missions();
            case TOP_HOUSES, WORST_HOUSES, RICHEST_HOUSES, RICHEST_PLAYERS -> 0;
        };
    }

    /**
     * O nome desempata, sempre no mesmo sentido, para o placar nao trocar duas linhas de lugar
     * sozinho entre um refresh e outro quando dois jogadores empatam.
     */
    private static void sortAndTrim(List<RankedEntry> entries, int limit, boolean ascending) {
        Comparator<RankedEntry> byValue = Comparator.comparingInt(RankedEntry::value);
        entries.sort((ascending ? byValue : byValue.reversed())
                .thenComparing(RankedEntry::label, String.CASE_INSENSITIVE_ORDER));
        if (entries.size() > limit) {
            entries.subList(limit, entries.size()).clear();
        }
    }

    private PlayerRanking ranking(UUID id, String name) {
        PlayerRanking existing = players.get(id);
        return existing == null ? PlayerRanking.of(name) : existing.withName(name);
    }

    /** Soma que satura em vez de dar a volta: staff distraida com um {@code /pontos} enorme nao vira ponto negativo. */
    private static int safeAdd(int value, int delta) {
        return (int) Mth.clamp((long) value + delta, Integer.MIN_VALUE, Integer.MAX_VALUE);
    }
}
