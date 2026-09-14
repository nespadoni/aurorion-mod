package com.aurorion.mundos.world;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * O formato {@code <dimensao>=<seed>} e digitado a mao num TOML, e quem digita esta configurando um
 * servidor de producao. O que estes testes fixam e que um erro numa linha custa <b>aquela</b> linha —
 * nunca o boot inteiro, nunca um mundo silenciosamente gerado com o seed errado.
 */
class WorldCatalogTest {
    private static final ResourceKey<Level> MUNDO_DOIS = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath("aurorion_mundos", "mundo_dois"));

    @Test
    void readsDimensionAndSeed() {
        Map<ResourceKey<Level>, Long> seeds = WorldCatalog.parseSeeds(
                List.of("aurorion_mundos:mundo_dois=284119730051"));

        assertEquals(284119730051L, seeds.get(MUNDO_DOIS));
    }

    @Test
    void acceptsNegativeSeedsAndSurroundingSpaces() {
        Map<ResourceKey<Level>, Long> seeds = WorldCatalog.parseSeeds(
                List.of("  aurorion_mundos:mundo_dois  =  -762045118829  "));

        assertEquals(-762045118829L, seeds.get(MUNDO_DOIS));
    }

    @Test
    void aBrokenLineDoesNotTakeDownTheGoodOnes() {
        Map<ResourceKey<Level>, Long> seeds = WorldCatalog.parseSeeds(List.of(
                "sem sinal de igual",
                "aurorion_mundos:mundo_tres=nao_e_numero",
                "NAO :: E UM ID=123",
                "aurorion_mundos:mundo_dois=7"));

        assertEquals(1, seeds.size(), "so a linha boa entra");
        assertEquals(7L, seeds.get(MUNDO_DOIS));
    }

    /**
     * A ausencia e o que protege dimensao de mod de terceiro: sem seed declarado, este mod nao toca
     * no terreno dela nem na barreira dela (SDD §2).
     */
    @Test
    void anUndeclaredDimensionIsNotOurs() {
        Map<ResourceKey<Level>, Long> seeds = WorldCatalog.parseSeeds(
                List.of("aurorion_mundos:mundo_dois=7"));

        assertFalse(seeds.containsKey(Level.OVERWORLD));
        assertFalse(seeds.containsKey(Level.NETHER));
        assertTrue(seeds.containsKey(MUNDO_DOIS));
    }

    @Test
    void anEmptyListDeclaresNoWorlds() {
        assertTrue(WorldCatalog.parseSeeds(List.of()).isEmpty());
    }
}
