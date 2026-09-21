package com.aurorion.areas;

import com.aurorion.areas.data.AreaData;
import com.aurorion.areas.data.AreaJson;
import com.aurorion.areas.geometry.AreaShape;
import com.aurorion.areas.geometry.AreaVolume;
import com.aurorion.areas.region.AreaRegion;
import com.aurorion.areas.rules.AreaRules;
import com.aurorion.areas.rules.Decision;
import com.aurorion.areas.rules.ResolvedRules;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A dona da area: o dado que a barreira de casa consulta.
 *
 * <p>O empurrao em si depende de um {@code ServerPlayer} e de rede, entao nao cabe em teste de
 * unidade; o que cabe — e e onde um erro passaria despercebido — e <b>qual</b> casa vale numa
 * posicao e se o vinculo sobrevive as edicoes da staff e ao save.
 */
class AreaHouseTest {
    private static final ResourceLocation WORLD = ResourceLocation.parse("minecraft:overworld");
    private static final ResourceLocation SYLVARA = ResourceLocation.parse("aurorion_ethereal:sylvara");
    private static final ResourceLocation IGNIVAR = ResourceLocation.parse("aurorion_ethereal:ignivar");
    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static AreaRegion region(String id, int priority, double radius) {
        return new AreaRegion(id, id, WORLD, priority, true,
                new AreaVolume(List.of(AreaShape.circle(0, 0, radius, 0, 100)), List.of()), AreaRules.INHERIT, Map.of());
    }

    @Test void theHighestPriorityHouseAreaOwnsThePosition() {
        var data = new AreaData();
        data.put(region("bairro", 10, 100).withHouse(SYLVARA));
        data.put(region("embaixada", 20, 5).withHouse(IGNIVAR));
        data.put(region("praca", 30, 2));

        var out = new ResolvedRules();
        data.resolve(WORLD, 0, 50, 0, ALICE, out);
        // A praca tem prioridade maior, mas nao declara casa: quem nao opina nao desempata.
        assertEquals(IGNIVAR, out.houseArea().house());
        assertEquals("embaixada", out.houseArea().id());

        data.resolve(WORLD, 10, 50, 0, ALICE, out);
        assertEquals(SYLVARA, out.houseArea().house());

        data.resolve(WORLD, 200, 50, 0, ALICE, out);
        assertNull(out.houseArea(), "fora de qualquer area de casa nao ha dona");
    }

    @Test void aReusedResolutionNeverKeepsThePreviousPositionsHouse() {
        var data = new AreaData();
        data.put(region("bairro", 10, 20).withHouse(SYLVARA));
        var out = new ResolvedRules();
        data.resolve(WORLD, 0, 50, 0, ALICE, out);
        assertNotNull(out.houseArea());
        data.resolve(WORLD, 500, 50, 0, ALICE, out);
        assertNull(out.houseArea());
    }

    @Test void aDisabledHouseAreaStopsOwningAnything() {
        var data = new AreaData();
        data.put(region("bairro", 10, 20).withHouse(SYLVARA).withEnabled(false));
        var out = new ResolvedRules();
        data.resolve(WORLD, 0, 50, 0, ALICE, out);
        assertNull(out.houseArea());
    }

    @Test void theOwnerSurvivesEveryEditAndTheRoundTrip() {
        var source = region("bairro", 10, 20).withHouse(SYLVARA);
        // Cada withX tem que carregar a dona adiante; esquecer um deles apagaria o vinculo em silencio.
        assertEquals(SYLVARA, source.withName("Bairro de Sylvara").house());
        assertEquals(SYLVARA, source.withPriority(50).house());
        assertEquals(SYLVARA, source.withEnabled(false).house());
        assertEquals(SYLVARA, source.withRules(AreaRules.INHERIT.withFlag("voo", Decision.DENY)).house());
        assertEquals(SYLVARA, source.withVolume(source.volume()).house());
        assertEquals(SYLVARA, source.withException(ALICE, "voo", true).house());
        assertNull(source.withHouse(null).house());

        assertEquals(SYLVARA, AreaJson.readRegion(AreaJson.writeRegion(source)).house());
        assertNull(AreaJson.readRegion(AreaJson.writeRegion(region("livre", 0, 5))).house(),
                "area sem dona nao pode ganhar uma na volta");
    }

    /** O reset de personagem reescreve a area inteira; a dona nao pode cair nessa reescrita. */
    @Test void aCharacterResetKeepsTheOwner() {
        var data = new AreaData();
        data.put(region("bairro", 10, 20).withHouse(SYLVARA).withException(ALICE, "voo", true));
        data.clearCharacter(ALICE);
        assertEquals(SYLVARA, data.require("bairro").house());
        assertTrue(data.require("bairro").exceptions().isEmpty());
    }

    /** Um save gravado antes de existir dona precisa continuar carregando, como area sem casa. */
    @Test void aSaveWithoutTheFieldLoadsAsHouseless() {
        var json = AreaJson.writeRegion(region("antiga", 0, 5));
        assertFalse(json.has("house"));
        assertNull(AreaJson.readRegion(json).house());
    }
}
