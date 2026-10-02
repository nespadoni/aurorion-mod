package com.aurorion.trama.skill;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static com.aurorion.trama.skill.Rating.*;
import static org.junit.jupiter.api.Assertions.*;

class BuildTest {
    private Build.Context context(double hp) { return new Build.Context(hp,true,true,true,true,true,true,true,true,true); }
    private Set<String> allNodes() {
        try (var reader = new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream(
                "/data/aurorion_trama/aurorion/trama/catalog.json")),StandardCharsets.UTF_8)) {
            Set<String> result = new HashSet<>();
            for (var n : JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("nodes"))
                result.add(n.getAsJsonObject().get("id").getAsString());
            return result;
        } catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
    }
    @Test void aggregateCapsEvenImpossibleAdministrativeBuilds() {
        var nodes = allNodes();
        nodes.removeAll(Set.of("C18","C20"));
        var build = new Build(nodes);
        for (double hp : List.of(.2,.5,.9)) {
            var e = build.effects(context(hp));
            assertTrue(e.get(MOV)<=.07); assertTrue(e.get(APS)<=.08);
            assertTrue(e.get(VIT)<=.10); assertTrue(e.get(MR)<=.12);
            assertTrue(e.get(BREACH)<=.30+1e-12); assertTrue(e.get(EREACH)<=.10);
            assertTrue(build.damage(e,MEL,100)<=.13);
            assertTrue(build.damage(e,RNG,100)<=.13);
            assertTrue(build.damage(e,MAG,100)<=.16);
        }
    }
    @Test void anchorCancelsConditionalMovementAndHybridCapsAllFamilies() {
        var nodes = allNodes();
        var anchor = new Build(nodes).effects(context(.5));
        assertEquals(0,anchor.get(MOV)); assertTrue(anchor.get(ARM)<=3);
        nodes.remove("C20");
        var hybrid = new Build(nodes); var e = hybrid.effects(context(.5));
        for (Rating family : List.of(MEL,RNG,MAG)) assertTrue(hybrid.damage(e,family,100)<=.06);
    }
    @Test void respecEmptyBuildLeavesNoRatingsOrBonuses() {
        var empty = new Build(Set.of()); var e = empty.effects(context(.5));
        for (Rating r : Rating.values()) assertEquals(0,e.get(r));
    }
    @Test void extractedCentralFiosHaveTheirRatings() {
        var b = new Build(Set.of("C01","C02","C03","C04","C05"));
        for (Rating r : List.of(VIT,ARM,APS,MAG,MOV)) assertEquals(1,b.rating(r));
    }
}
