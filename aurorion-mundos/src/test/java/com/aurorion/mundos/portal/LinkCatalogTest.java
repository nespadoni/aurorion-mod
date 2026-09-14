package com.aurorion.mundos.portal;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LinkCatalogTest {
    private static final ResourceKey<Level> MUNDO_DOIS = dimension("mundo_dois");
    private static final ResourceKey<Level> MUNDO_TRES = dimension("mundo_tres");

    @Test
    void withoutAnyLinkTheVanillaKeepsDeciding() {
        assertNull(LinkCatalog.pick(null, 0.0D, 0.0D),
                "sem ligacao para a dimensao, o portal do Nether tem que continuar sendo do Nether");
    }

    @Test
    void aDeclaredCircleWins() {
        DimensionLink generic = link(MUNDO_DOIS, null);
        DimensionLink inCircle = link(MUNDO_TRES, new DimensionLink.Area(2000, 0, 128));

        assertSame(inCircle, LinkCatalog.pick(List.of(generic, inCircle), 2000.0D, 0.0D));
        assertSame(inCircle, LinkCatalog.pick(List.of(generic, inCircle), 2100.0D, 50.0D));
    }

    /**
     * O circulo ganha por ser mais especifico, e nao por vir antes. Se a ordem decidisse, a topologia
     * dos mundos dependeria de um campo que existe para ordenar a listagem do comando.
     */
    @Test
    void theCircleWinsEvenWhenTheGenericComesFirstInTheList() {
        DimensionLink generic = link(MUNDO_DOIS, null);
        DimensionLink inCircle = link(MUNDO_TRES, new DimensionLink.Area(0, 0, 100));

        assertSame(inCircle, LinkCatalog.pick(List.of(generic, inCircle), 10.0D, 10.0D));
    }

    @Test
    void outsideEveryCircleFallsBackToTheLinkWithoutArea() {
        DimensionLink generic = link(MUNDO_DOIS, null);
        DimensionLink inCircle = link(MUNDO_TRES, new DimensionLink.Area(2000, 0, 128));

        assertSame(generic, LinkCatalog.pick(List.of(inCircle, generic), 0.0D, 0.0D));
    }

    /** Sem ligacao generica, quem esta fora de todo circulo cai no vanilla — nao num destino qualquer. */
    @Test
    void outsideEveryCircleWithoutFallbackMeansVanilla() {
        DimensionLink inCircle = link(MUNDO_TRES, new DimensionLink.Area(2000, 0, 128));

        assertNull(LinkCatalog.pick(List.of(inCircle), 0.0D, 0.0D));
    }

    @Test
    void theCircleIsRoundAndNotSquare() {
        DimensionLink.Area area = new DimensionLink.Area(0, 0, 100);

        assertTrue(area.contains(100.0D, 0.0D), "a borda exata conta como dentro");
        assertTrue(area.contains(70.0D, 70.0D), "70,70 esta a ~99 blocos do centro");
        assertTrue(!area.contains(71.0D, 71.0D), "71,71 esta a ~100,4 — fora, ainda que caiba no quadrado");
    }

    private static DimensionLink link(ResourceKey<Level> to, DimensionLink.Area area) {
        return new DimensionLink(
                ResourceLocation.fromNamespaceAndPath("aurorion_mundos", "teste_" + to.location().getPath()),
                Level.OVERWORLD,
                to,
                Optional.empty(),
                Optional.ofNullable(area),
                0);
    }

    private static ResourceKey<Level> dimension(String path) {
        return ResourceKey.create(Registries.DIMENSION,
                ResourceLocation.fromNamespaceAndPath("aurorion_mundos", path));
    }
}
