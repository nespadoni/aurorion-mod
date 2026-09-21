package com.aurorion.areas;

import com.aurorion.areas.data.*;
import com.aurorion.areas.geometry.*;
import com.aurorion.areas.region.*;
import com.aurorion.areas.rules.*;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AreaPolicyTest {
    private static final ResourceLocation WORLD = ResourceLocation.parse("minecraft:overworld");
    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static AreaRegion region(String id, int priority, double radius, AreaRules rules) {
        return new AreaRegion(id, id, WORLD, priority, true,
                new AreaVolume(List.of(AreaShape.circle(0, 0, radius, 0, 100)), List.of()), rules, Map.of());
    }
    private static AreaRules rules(String key, Decision decision) { return AreaRules.INHERIT.withFlag(key, decision); }
    @Test void classroomOnlyOverridesMagicAndInheritsSchoolFlightAndSafety() {
        var data = new AreaData();
        data.put(region("school", 10, 100, new AreaRules(Map.of("voo", Decision.DENY, "magia", Decision.DENY,
                "monstros", Decision.DENY), null, -1, -1)));
        data.put(region("classroom", 20, 5, rules("magia", Decision.ALLOW)));
        var out = new ResolvedRules();
        data.resolve(WORLD, 0, 50, 0, ALICE, out);
        assertTrue(out.allows(AreaRule.MAGIC));
        assertFalse(out.allows(AreaRule.FLIGHT));
        assertFalse(out.allows(AreaRule.HOSTILE_SPAWN));
        assertEquals("school", out.owner(AreaRule.FLIGHT).id());
        data.resolve(WORLD, 10, 50, 0, ALICE, out);
        assertFalse(out.allows(AreaRule.MAGIC));
        data.resolve(WORLD, 101, 50, 0, ALICE, out);
        assertTrue(out.allows(AreaRule.FLIGHT));
    }
    @Test void exceptionsBelongToOneCharacterAndOneRuleAndResetWithCharacter() {
        var data = new AreaData();
        data.put(region("school", 10, 100, rules("voo", Decision.DENY).withFlag("magia", Decision.DENY))
                .withException(ALICE, "voo", true));
        assertTrue(data.allows(WORLD, 0, 50, 0, ALICE, "voo"));
        assertFalse(data.allows(WORLD, 0, 50, 0, ALICE, "magia"));
        assertFalse(data.allows(WORLD, 0, 50, 0, BOB, "voo"));
        data.clearCharacter(ALICE);
        assertFalse(data.allows(WORLD, 0, 50, 0, ALICE, "voo"));
        data.clearCharacter(ALICE); // Reset is idempotent.
    }
    @Test void exceptionDoesNotOverrideAHigherPriorityExplicitRule() {
        var data = new AreaData();
        data.put(region("school", 10, 100, rules("voo", Decision.DENY)).withException(ALICE, "voo", true));
        data.put(region("vault", 50, 5, rules("voo", Decision.DENY)));
        assertFalse(data.allows(WORLD, 0, 50, 0, ALICE, "voo"));
        assertTrue(data.allows(WORLD, 20, 50, 0, ALICE, "voo"));
    }
    @Test void tiesAreIndependentOfInsertionOrderAndDisabledRegionsAreIgnored() {
        var a = region("a", 10, 10, rules("voo", Decision.DENY));
        var b = region("b", 10, 10, rules("voo", Decision.ALLOW));
        for (var order : List.of(List.of(a, b), List.of(b, a))) {
            var out = new ResolvedRules(); out.reset(AreaRules.INHERIT, null);
            new AreaIndex(order).resolve(0, 50, 0, out);
            assertFalse(out.allows(AreaRule.FLIGHT));
        }
        var out = new ResolvedRules(); out.reset(AreaRules.INHERIT, null);
        new AreaIndex(List.of(a.withEnabled(false), b)).resolve(0, 50, 0, out);
        assertTrue(out.allows(AreaRule.FLIGHT));
    }
    @Test void dimensionsDefaultsCustomRulesAndAmbienceAreIndependent() {
        var data = new AreaData();
        var forest = ResourceLocation.parse("aurorion_areas:floresta_negra");
        data.setDefaults(WORLD, new AreaRules(Map.of("profissoes:coletar", Decision.DENY), forest, 2, 1.5));
        data.put(region("refuge", 20, 10, new AreaRules(Map.of("profissoes:coletar", Decision.ALLOW),
                AreaRules.NO_AMBIENCE, 1, -1)));
        var out = new ResolvedRules();
        data.resolve(WORLD, 0, 50, 0, ALICE, out);
        assertEquals(AreaRules.NO_AMBIENCE, out.ambience());
        assertEquals(1, out.mobHealth()); assertEquals(1.5, out.mobDamage());
        assertTrue(data.allows(WORLD, 0, 50, 0, ALICE, "profissoes:coletar"));
        assertFalse(data.allows(WORLD, 20, 50, 0, ALICE, "profissoes:coletar"));
        data.resolve(ResourceLocation.parse("minecraft:the_nether"), 0, 50, 0, ALICE, out);
        assertEquals(1, out.mobHealth()); assertEquals(AreaRules.NO_AMBIENCE, out.ambience());
    }
    @Test void persistenceRetainsHolesRulesAndCharacterExceptions() {
        var source = region("school", 12, 30, rules("voo", Decision.DENY))
                .withVolume(new AreaVolume(List.of(AreaShape.circle(0, 0, 30, 0, 100)),
                        List.of(AreaShape.circle(0, 0, 2, 20, 30))))
                .withException(ALICE, "voo", true);
        var restored = AreaJson.readRegion(AreaJson.writeRegion(source));
        assertEquals(source.id(), restored.id()); assertEquals(source.rules(), restored.rules());
        assertEquals(source.exceptions(), restored.exceptions());
        assertFalse(restored.volume().contains(0, 25, 0));
        assertTrue(restored.volume().contains(0, 40, 0));
    }
    @Test void reusedResolutionRefreshesDefaultsAfterStaffEdits() {
        var data = new AreaData();
        var out = new ResolvedRules();
        data.resolve(WORLD, 0, 50, 0, ALICE, out);
        assertTrue(out.allows(AreaRule.FLIGHT));
        var forest = ResourceLocation.parse("aurorion_areas:floresta_negra");
        data.setDefaults(WORLD, new AreaRules(Map.of("voo", Decision.DENY), forest, 2, 1.5));
        data.resolve(WORLD, 0, 50, 0, ALICE, out);
        assertFalse(out.allows(AreaRule.FLIGHT));
        assertEquals(forest, out.ambience());
        assertEquals(2, out.mobHealth());
        assertEquals(1.5, out.mobDamage());
        data.setDefaults(WORLD, AreaRules.INHERIT);
        data.resolve(WORLD, 0, 50, 0, ALICE, out);
        assertTrue(out.allows(AreaRule.FLIGHT));
        assertEquals(AreaRules.NO_AMBIENCE, out.ambience());
        assertEquals(1, out.mobHealth());
        assertEquals(1, out.mobDamage());
    }
    @Test void reusedResolutionDoesNotKeepAnotherCharactersExceptions() {
        var data = new AreaData();
        data.put(region("school", 10, 100, rules("voo", Decision.DENY)).withException(ALICE, "voo", true));
        var out = new ResolvedRules();
        data.resolve(WORLD, 0, 50, 0, ALICE, out);
        assertTrue(out.allows(AreaRule.FLIGHT));
        data.resolve(WORLD, 0, 50, 0, BOB, out);
        assertFalse(out.allows(AreaRule.FLIGHT));
        data.clearCharacter(ALICE);
        data.resolve(WORLD, 0, 50, 0, ALICE, out);
        assertFalse(out.allows(AreaRule.FLIGHT));
    }
    /**
     * Concessao e proibicao sao leituras opostas do mesmo cadastro, e trocar uma pela outra nao
     * quebra nada visivel: daria agua pura ao mapa inteiro por omissao, que e o pior default
     * possivel e so apareceria em jogo.
     */
    @Test void grantedIsOptInWhileAllowsIsOptOut() {
        var data = new AreaData();
        String key = "agua_pura";

        // Sem area nenhuma: 'allows' diz sim (nada proibiu), 'granted' diz nao (ninguem concedeu).
        assertTrue(data.allows(WORLD, 0, 50, 0, ALICE, key));
        assertFalse(data.granted(WORLD, 0, 50, 0, ALICE, key));

        data.put(region("academia", 10, 50, rules(key, Decision.ALLOW)));
        assertTrue(data.granted(WORLD, 0, 50, 0, ALICE, key));
        assertFalse(data.granted(WORLD, 200, 50, 0, ALICE, key), "fora da area a concessao acaba");

        // Uma area interna de prioridade maior pode tirar a concessao de um trecho.
        data.put(region("laboratorio", 20, 5, rules(key, Decision.DENY)));
        assertFalse(data.granted(WORLD, 0, 50, 0, ALICE, key));
        assertTrue(data.granted(WORLD, 10, 50, 0, ALICE, key));

        // Desativar a area que concede tira a concessao junto.
        data.put(data.require("academia").withEnabled(false));
        assertFalse(data.granted(WORLD, 10, 50, 0, ALICE, key));
    }

    /** Excecao individual concede a uma pessoa so, sem abrir a area inteira. */
    @Test void aGrantCanBeGivenToOneCharacterOnly() {
        var data = new AreaData();
        String key = "agua_pura";
        data.put(region("poco", 10, 20, AreaRules.INHERIT).withException(ALICE, key, true));
        assertTrue(data.granted(WORLD, 0, 50, 0, ALICE, key));
        assertFalse(data.granted(WORLD, 0, 50, 0, BOB, key));
        assertFalse(data.granted(WORLD, 0, 50, 0, null, key));
    }

    /** O padrao da dimensao tambem pode conceder — util para um mundo inteiro de agua tratada. */
    @Test void aDimensionDefaultCanGrantToo() {
        var data = new AreaData();
        String key = "agua_pura";
        assertFalse(data.granted(WORLD, 0, 50, 0, ALICE, key));
        data.setDefaults(WORLD, AreaRules.INHERIT.withFlag(key, Decision.ALLOW));
        assertTrue(data.granted(WORLD, 0, 50, 0, ALICE, key));
        assertFalse(data.granted(ResourceLocation.parse("minecraft:the_nether"), 0, 50, 0, ALICE, key));
    }

    @Test void bvhMatchesExhaustiveResolutionAcrossManyRegions() {
        var regions = new ArrayList<AreaRegion>();
        for (int i = 0; i < 40; i++) regions.add(new AreaRegion("r" + i, "region " + i, WORLD, i % 7, i % 9 != 0,
                new AreaVolume(List.of(AreaShape.circle(i * 13 - 200, i % 4 * 12, 25, 0, 100)), List.of()),
                rules(i % 2 == 0 ? "voo" : "magia", i % 3 == 0 ? Decision.ALLOW : Decision.DENY), Map.of()));
        var index = new AreaIndex(regions); var indexed = new ResolvedRules(); var exhaustive = new ResolvedRules();
        for (int x = -230; x < 370; x += 7) for (int z = -30; z < 80; z += 9) {
            indexed.reset(AreaRules.INHERIT, ALICE); exhaustive.reset(AreaRules.INHERIT, ALICE);
            index.resolve(x, 50, z, indexed);
            for (var region : regions) if (region.enabled() && region.volume().contains(x, 50, z)) exhaustive.include(region);
            for (var rule : AreaRule.ALL) {
                assertEquals(exhaustive.allows(rule), indexed.allows(rule));
                assertEquals(exhaustive.owner(rule), indexed.owner(rule));
            }
        }
    }
}
