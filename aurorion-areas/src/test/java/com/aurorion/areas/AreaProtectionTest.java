package com.aurorion.areas;

import com.aurorion.areas.data.AreaData;
import com.aurorion.areas.geometry.AreaShape;
import com.aurorion.areas.geometry.AreaVolume;
import com.aurorion.areas.region.AreaRegion;
import com.aurorion.areas.rules.AreaRules;
import com.aurorion.areas.rules.Decision;
import com.aurorion.areas.rules.Protection;
import com.aurorion.areas.rules.ProtectionRule;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Politica de protecao de blocos ({@code AreaData#protection}). Os listeners dependem de mundo e
 * jogador; o que cabe aqui — e onde um erro abriria a casa de alguem — e a decisao em si.
 */
class AreaProtectionTest {
    private static final ResourceLocation WORLD = ResourceLocation.parse("minecraft:overworld");
    private static final ResourceLocation NETHER = ResourceLocation.parse("minecraft:the_nether");
    private static final ResourceLocation SYLVARA = ResourceLocation.parse("aurorion_ethereal:sylvara");
    private static final ResourceLocation IGNIVAR = ResourceLocation.parse("aurorion_ethereal:ignivar");
    private static final UUID MEMBER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID STRANGER = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private static AreaRegion region(String id, int priority, double radius, AreaRules rules) {
        return new AreaRegion(id, id, WORLD, priority, true,
                new AreaVolume(List.of(AreaShape.circle(0, 0, radius, 0, 100)), List.of()), rules, Map.of());
    }

    private static boolean denied(AreaData data, double x, ProtectionRule rule, UUID actor, ResourceLocation house) {
        return data.protection(WORLD, x, 50, 0, rule, actor, house, true).denied();
    }

    @Test void ordinaryAreasKeepAllowingUntilStaffDenies() {
        var data = new AreaData();
        data.put(region("praca", 10, 20, AreaRules.INHERIT));
        assertFalse(data.guarded(WORLD), "area comum sem regra negada nao liga protecao nenhuma");
        for (ProtectionRule rule : ProtectionRule.ALL) assertFalse(denied(data, 0, rule, STRANGER, null));

        data.put(data.require("praca").withRules(AreaRules.INHERIT.withFlag("construir", Decision.DENY)));
        assertTrue(data.guarded(WORLD));
        assertTrue(denied(data, 0, ProtectionRule.BUILD, STRANGER, null));
        assertFalse(denied(data, 0, ProtectionRule.INTERACT, STRANGER, null), "cada regra e independente");
        assertFalse(denied(data, 50, ProtectionRule.BUILD, STRANGER, null), "fora da area nada muda");
        assertFalse(data.guarded(NETHER));
    }

    @Test void houseAreasAreClosedToNonMembersByDefault() {
        var data = new AreaData();
        data.put(region("casa", 10, 20, AreaRules.INHERIT).withHouse(SYLVARA));
        assertTrue(data.guarded(WORLD));
        for (ProtectionRule rule : ProtectionRule.ALL) {
            assertTrue(denied(data, 0, rule, STRANGER, IGNIVAR), rule.key() + " de outra casa");
            assertTrue(denied(data, 0, rule, STRANGER, null), rule.key() + " sem casa");
            assertFalse(denied(data, 0, rule, MEMBER, SYLVARA), rule.key() + " de quem e da casa");
        }
        // Ambiente (creeper, agua de fora) nao tem casa: negado.
        assertTrue(data.protection(WORLD, 0, 50, 0, ProtectionRule.EXPLOSION, null, null, true).denied());
    }

    @Test void withoutAHousesModHouseAreasBehaveLikeOrdinaryOnes() {
        var data = new AreaData();
        data.put(region("casa", 10, 20, AreaRules.INHERIT).withHouse(SYLVARA));
        assertFalse(data.protection(WORLD, 0, 50, 0, ProtectionRule.BUILD, STRANGER, null, false).denied());
    }

    @Test void explicitRulesAndHigherPriorityAreasOverrideTheHouseDefault() {
        var data = new AreaData();
        data.put(region("casa", 10, 50, AreaRules.INHERIT).withHouse(SYLVARA));
        // Um patio publico de prioridade maior dentro da casa.
        data.put(region("patio", 20, 5, AreaRules.INHERIT.withFlag("interagir", Decision.ALLOW)));
        assertFalse(denied(data, 0, ProtectionRule.INTERACT, STRANGER, null));
        assertTrue(denied(data, 0, ProtectionRule.BUILD, STRANGER, null), "o patio so opina sobre interagir");
        assertTrue(denied(data, 10, ProtectionRule.INTERACT, STRANGER, null));

        // Uma area de prioridade menor nao abre a casa.
        data.put(region("bairro", 1, 100, AreaRules.INHERIT.withFlag("construir", Decision.ALLOW)));
        assertTrue(denied(data, 10, ProtectionRule.BUILD, STRANGER, null));

        // A propria casa abrindo explicitamente (evento): todo mundo constroi.
        data.put(data.require("casa").withRules(AreaRules.INHERIT.withFlag("construir", Decision.ALLOW)));
        assertFalse(denied(data, 10, ProtectionRule.BUILD, STRANGER, null));
    }

    @Test void theVisitorPassOpensDoorsButNotWalls() {
        var data = new AreaData();
        data.put(region("casa", 10, 20, AreaRules.INHERIT).withHouse(SYLVARA)
                .withException(STRANGER, ProtectionRule.VISITOR_PASS, true));
        assertFalse(denied(data, 0, ProtectionRule.INTERACT, STRANGER, null));
        assertTrue(denied(data, 0, ProtectionRule.BUILD, STRANGER, null));
        // Excecao individual de construir tambem vale, como qualquer outra regra.
        data.put(data.require("casa").withException(STRANGER, "construir", true));
        assertFalse(denied(data, 0, ProtectionRule.BUILD, STRANGER, null));
    }

    @Test void aMemberKeepsAccessEvenWhenTheHouseDeniesExplicitly() {
        var data = new AreaData();
        data.put(region("casa", 10, 20, AreaRules.INHERIT.withFlag("construir", Decision.DENY)).withHouse(SYLVARA));
        assertFalse(denied(data, 0, ProtectionRule.BUILD, MEMBER, SYLVARA));
        assertTrue(denied(data, 0, ProtectionRule.BUILD, STRANGER, null));
    }

    @Test void crossingComparesWhoProtectsEachSide() {
        var data = new AreaData();
        data.put(region("casa", 10, 20, AreaRules.INHERIT).withHouse(SYLVARA));
        Protection inside = data.protection(WORLD, 0, 50, 0, ProtectionRule.FLUID, null, null, true);
        Protection alsoInside = data.protection(WORLD, 5, 50, 0, ProtectionRule.FLUID, null, null, true);
        Protection outside = data.protection(WORLD, 40, 50, 0, ProtectionRule.FLUID, null, null, true);
        assertTrue(Protection.crosses(outside, inside), "lava de fora entrando");
        assertFalse(Protection.crosses(inside, alsoInside), "agua escorrendo dentro da propria casa");
        assertFalse(Protection.crosses(inside, outside), "saindo da casa nao invade nada");

        // Duas casas coladas: da embaixada para o bairro tambem e invasao.
        data.put(region("embaixada", 20, 3, AreaRules.INHERIT).withHouse(IGNIVAR));
        Protection embassy = data.protection(WORLD, 0, 50, 0, ProtectionRule.FLUID, null, null, true);
        Protection district = data.protection(WORLD, 10, 50, 0, ProtectionRule.FLUID, null, null, true);
        assertTrue(Protection.crosses(embassy, district));
    }

    @Test void aDimensionDefaultCanProtectTheWholeWorld() {
        var data = new AreaData();
        data.setDefaults(WORLD, AreaRules.INHERIT.withFlag("explosao", Decision.DENY));
        assertTrue(data.guarded(WORLD));
        assertTrue(data.protection(WORLD, 999, 50, 999, ProtectionRule.EXPLOSION, null, null, true).denied());
        data.put(region("pedreira", 10, 20, AreaRules.INHERIT.withFlag("explosao", Decision.ALLOW)));
        assertFalse(data.protection(WORLD, 0, 50, 0, ProtectionRule.EXPLOSION, null, null, true).denied());
    }

    @Test void disabledAreasStopProtecting() {
        var data = new AreaData();
        data.put(region("casa", 10, 20, AreaRules.INHERIT).withHouse(SYLVARA).withEnabled(false));
        assertFalse(data.guarded(WORLD));
        assertFalse(denied(data, 0, ProtectionRule.BUILD, STRANGER, null));
    }

    /**
     * O {@code FlowingFluidSpreadMixin} usa {@code require = 0} com descritor completo: se o vanilla
     * mudar a assinatura, a regra {@code fluido} pararia de barrar escoamento em silencio. Este teste
     * acusa a mudanca no build.
     */
    @Test void flowingFluidStillHasTheSpreadCheckTheMixinTargets() throws NoSuchMethodException {
        var method = FlowingFluid.class.getDeclaredMethod("canSpreadTo", BlockGetter.class, BlockPos.class,
                BlockState.class, Direction.class, BlockPos.class, BlockState.class, FluidState.class, Fluid.class);
        assertEquals(boolean.class, method.getReturnType());
    }
}
