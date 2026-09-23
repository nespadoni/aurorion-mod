package com.aurorion.profissoes;

import com.aurorion.profissoes.data.Profession;
import com.aurorion.profissoes.npc.NpcConfigParser;
import com.aurorion.profissoes.npc.NpcDefinition.*;
import com.aurorion.profissoes.npc.NpcStockData;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

class NpcConfigParserTest {
    @Test void requestedFormatWithEnglishProfessionAliasLoads() {
        var result = NpcConfigParser.parse("""
                {"npcs":[
                  {"id":"medico_principal","display_name":"Médico do Vilarejo","profession":"medico",
                   "services":[{"name":"Cura Completa","cost_item":"minecraft:emerald","cost_amount":5,
                                "command":"/bodydamage heal {player} all 100"}]},
                  {"id":"ferreiro_vila","display_name":"Ferreiro Mestre","profession":"blacksmith",
                   "trades":[{"item_id":"minecraft:diamond_sword","amount":1,"price_item":"minecraft:emerald",
                              "price_amount":10,"max_stock":5}]}
                ]}""");
        assertEquals(java.util.List.of(), result.errors());
        var doctor = result.npcs().get(0);
        assertEquals(Profession.DOCTOR, doctor.profession());
        var service = doctor.services().get(0);
        assertEquals(ActionType.COMMAND, service.action());
        assertEquals(java.util.List.of("bodydamage heal {player} all 100"), service.commands());
        assertEquals(new Cost("minecraft:emerald", 5, 0), service.cost());
        var smith = result.npcs().get(1);
        assertEquals(Profession.SMITH, smith.profession());
        var trade = smith.trades().get(0);
        assertEquals(5, trade.maxStock());
        assertEquals(Restock.RESTART, trade.restock());
        assertEquals("0:minecraft:diamond_sword", trade.key());
    }

    @Test void aliasesMoneyAndInfiniteStock() {
        var result = NpcConfigParser.parse("""
                {"npcs":[{"id":"mercador","profession":"mercador","trades":[
                  {"id":"tochas","item_id":"minecraft:torch","amount":16,"price_item_id":"minecraft:emerald",
                   "price_money":"2,5","stock_limit":-1,"restock":"diario","nbt_data":{"marca":1}}]}]}""");
        assertTrue(result.errors().isEmpty(), result.errors().toString());
        var npc = result.npcs().get(0);
        assertEquals(Profession.NONE, npc.profession());
        assertEquals("mercador", npc.displayName());
        var trade = npc.trades().get(0);
        assertEquals(new Cost("minecraft:emerald", 1, 25), trade.price());
        assertFalse(trade.limited());
        assertEquals(Restock.DAILY, trade.restock());
        assertEquals("{\"marca\":1}", trade.nbtData());
    }

    @Test void brokenPiecesAreDroppedAloneWithTheirPath() {
        var result = NpcConfigParser.parse("""
                {"npcs":[
                  {"id":"bom","services":[{"name":"Sem comando","action_type":"command"},
                                          {"name":"Cura","action_type":"heal"}],
                   "trades":[{"item_id":"minecraft:bread","price_amount":2},{"item_id":"minecraft:apple"}]},
                  {"id":"Inválido"},
                  {"id":"bom"}
                ]}""");
        assertEquals(1, result.npcs().size());
        var npc = result.npcs().get(0);
        assertEquals(1, npc.services().size());
        assertEquals(ActionType.HEAL, npc.services().get(0).action());
        assertEquals(1, npc.trades().size());
        assertEquals(Cost.FREE, npc.trades().get(0).price());
        assertEquals(4, result.errors().size(), result.errors().toString());
        assertTrue(result.errors().stream().anyMatch(e -> e.startsWith("npcs[0].trades[0].price_amount")));
        assertTrue(result.errors().stream().anyMatch(e -> e.startsWith("npcs[2].id")));
    }

    @Test void unreadableFileIsReportedAsUnreadable() {
        assertFalse(NpcConfigParser.parse("{ npcs: [").readable());
        assertFalse(NpcConfigParser.parse("[]").readable());
    }

    @Test void bundledExampleHasNoErrors() throws IOException {
        try (var in = NpcConfigParserTest.class.getResourceAsStream("/aurorion_profissoes/npcs.default.json")) {
            assertNotNull(in);
            var result = NpcConfigParser.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            assertTrue(result.errors().isEmpty(), result.errors().toString());
            assertEquals(5, result.npcs().size());
        }
    }

    @Test void stockResetsWhenThePeriodChanges() {
        var stock = new NpcStockData();
        long today = NpcStockData.period(Restock.DAILY, 1L, LocalDate.of(2026, 9, 23));
        long tomorrow = NpcStockData.period(Restock.DAILY, 1L, LocalDate.of(2026, 9, 24));
        stock.add("ferreiro/espada", today, 1);
        stock.add("ferreiro/espada", today, 1);
        assertEquals(2, stock.sold("ferreiro/espada", today));
        assertEquals(0, stock.sold("ferreiro/espada", tomorrow));
        assertNotEquals(NpcStockData.period(Restock.RESTART, 1L, LocalDate.MIN), NpcStockData.period(Restock.RESTART, 2L, LocalDate.MIN));
        assertEquals(1, stock.reset("ferreiro"));
        assertEquals(0, stock.sold("ferreiro/espada", today));
    }
}
