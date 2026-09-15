package com.aurorion.ethereal.ranking;

import com.aurorion.ethereal.house.House;
import com.aurorion.ethereal.house.HouseData;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RankingDataTest {
    private static final House IGNIVAR = house("ignivar", "Ignivar", 0xFF5533);
    private static final House SYLVARA = house("sylvara", "Sylvara", 0x3ECF6E);
    private static final House NYX = house("nyx", "Nyx", 0x8A5CF0);
    private static final List<House> CATALOG = List.of(IGNIVAR, SYLVARA, NYX);

    private static House house(String id, String name, int color) {
        return new House(ResourceLocation.fromNamespaceAndPath("aurorion_ethereal", id),
                Component.literal(name), CommonComponents.EMPTY, CommonComponents.EMPTY,
                color, Optional.empty(), House.UNLIMITED, 0, com.aurorion.ethereal.house.RiteStyle.DEFAULT);
    }

    /** O caso que o placar antigo nao cobria: ponto de aluno tem de aparecer no total da casa dele. */
    @Test
    void houseTotalIsBonusPlusMemberPoints() {
        RankingData data = new RankingData();
        HouseData membership = new HouseData();

        UUID member = UUID.randomUUID();
        membership.setHouse(member, IGNIVAR.id());
        data.adjustPlayerPoints(member, "Aluno", 30);
        data.adjustHouseBonus(IGNIVAR.id(), 12);

        assertEquals(42, data.houseTotals(membership, CATALOG).get(IGNIVAR.id()));
    }

    /** Ponto de quem nao tem casa conta so para o ranking de alunos. */
    @Test
    void pointsOfHouselessPlayersDoNotReachAnyHouse() {
        RankingData data = new RankingData();
        HouseData membership = new HouseData();

        data.adjustPlayerPoints(UUID.randomUUID(), "Sem casa", 100);

        assertEquals(0, data.houseTotals(membership, CATALOG).get(IGNIVAR.id()));
        assertEquals(0, data.houseTotals(membership, CATALOG).get(SYLVARA.id()));
    }

    /** Sair de uma casa leva os pontos junto: o total e a soma dos membros de agora. */
    @Test
    void leavingAHouseMovesThePointsWithThePlayer() {
        RankingData data = new RankingData();
        HouseData membership = new HouseData();

        UUID player = UUID.randomUUID();
        membership.setHouse(player, IGNIVAR.id());
        data.adjustPlayerPoints(player, "Aluno", 50);
        membership.setHouse(player, SYLVARA.id());

        assertEquals(0, data.houseTotals(membership, CATALOG).get(IGNIVAR.id()));
        assertEquals(50, data.houseTotals(membership, CATALOG).get(SYLVARA.id()));
    }

    @Test
    void sortsAndLimitsHousesByTotal() {
        RankingData data = new RankingData();
        HouseData membership = new HouseData();

        data.adjustHouseBonus(IGNIVAR.id(), 30);
        data.adjustHouseBonus(SYLVARA.id(), 18);
        data.adjustHouseBonus(NYX.id(), 12);

        List<RankedEntry> top = data.top(BoardMode.TOP_HOUSES, 2, membership, CATALOG);

        assertEquals(List.of("Ignivar", "Sylvara"), top.stream().map(RankedEntry::label).toList());
        assertEquals(List.of(30, 18), top.stream().map(RankedEntry::value).toList());
    }

    /** O modo "piores casas" e o mesmo ranking pelo outro lado, nao uma lista separada. */
    @Test
    void worstHousesInvertsTheOrder() {
        RankingData data = new RankingData();
        HouseData membership = new HouseData();

        data.adjustHouseBonus(IGNIVAR.id(), 30);
        data.adjustHouseBonus(SYLVARA.id(), 18);
        data.adjustHouseBonus(NYX.id(), 12);

        List<RankedEntry> worst = data.top(BoardMode.WORST_HOUSES, 2, membership, CATALOG);

        assertEquals(List.of("Nyx", "Sylvara"), worst.stream().map(RankedEntry::label).toList());
    }

    /** A linha de casa carrega a cor dela: e o que faz o holograma sair colorido. */
    @Test
    void houseEntriesCarryTheHouseColor() {
        RankingData data = new RankingData();

        List<RankedEntry> top = data.top(BoardMode.TOP_HOUSES, 3, new HouseData(), CATALOG);

        assertEquals(NYX.argb(), top.stream()
                .filter(entry -> entry.label().equals("Nyx"))
                .findFirst().orElseThrow().color());
    }

    @Test
    void recordsDeathsDuelsAndMissionsIndependently() {
        RankingData data = new RankingData();
        HouseData membership = new HouseData();
        UUID victim = UUID.randomUUID();
        UUID winner = UUID.randomUUID();

        data.recordDeath(victim, "Victim");
        data.recordDeath(victim, "Victim");
        data.recordDuelWin(winner, "Winner");
        data.recordMission(winner, "Winner");

        assertEquals(2, data.top(BoardMode.DEATHS, 1, membership, CATALOG).getFirst().value());
        assertEquals("Winner", data.top(BoardMode.DUEL_WINS, 1, membership, CATALOG).getFirst().label());
        assertEquals(1, data.top(BoardMode.MISSIONS, 1, membership, CATALOG).getFirst().value());
    }
}
