package com.aurorion.profissoes.craft;

import com.aurorion.profissoes.data.Profession;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

class CraftDifficultyTest {
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("aurorion_profissoes", "arcano");

    @Test void shippedArcaneRuleNeedsTheFirstAwakening() throws Exception {
        String path = "/data/aurorion_profissoes/aurorion/oficio/arcano.json";
        try (var reader = new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream(path)), StandardCharsets.UTF_8)) {
            var rule = CraftDifficulty.Rule.codec(ID).parse(JsonOps.INSTANCE, JsonParser.parseReader(reader)).getOrThrow();
            assertEquals(java.util.List.of("irons_spellbooks"), rule.mods());
            var entry = rule.entry();
            assertEquals(13, entry.dc());
            assertEquals("AR1", entry.node());
            assertEquals(Profession.ARCANIST, entry.domain());
        }
    }

    @Test void unknownProfessionFallsBackToNone() {
        var json = JsonParser.parseString("{\"items\":[\"minecraft:diamond_sword\"],\"cd\":15,\"profissao\":\"pirata\"}");
        var entry = CraftDifficulty.Rule.codec(ID).parse(JsonOps.INSTANCE, json).getOrThrow().entry();
        assertEquals(15, entry.dc());
        assertEquals("", entry.node());
        assertEquals(Profession.NONE, entry.domain());
    }
}
