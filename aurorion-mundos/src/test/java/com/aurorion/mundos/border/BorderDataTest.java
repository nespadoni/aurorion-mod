package com.aurorion.mundos.border;

import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.WorldBorder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * O que estes testes protegem e a promessa "reiniciar o servidor nao perde a barreira". Sem
 * persistencia, toda barreira ajustada pela staff voltaria ao valor do overworld no proximo boot —
 * que e exatamente o bug que este mod existe para nao ter.
 */
class BorderDataTest {
    private static final ResourceKey<Level> MUNDO_DOIS = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath("aurorion_mundos", "mundo_dois"));
    private static final ResourceKey<Level> MUNDO_TRES = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath("aurorion_mundos", "mundo_tres"));

    @Test
    void eachWorldKeepsItsOwnBorderAcrossSaveAndLoad() {
        BorderData data = new BorderData();
        data.remember(MUNDO_DOIS, border(4000.0D, 100.0D, -250.0D));
        data.remember(MUNDO_TRES, border(1200.0D, 0.0D, 0.0D));

        BorderData reloaded = BorderData.load(data.save(new CompoundTag(), null), null);

        BorderData.Settings dois = reloaded.get(MUNDO_DOIS);
        assertNotNull(dois);
        assertEquals(4000.0D, dois.size());
        assertEquals(100.0D, dois.centerX());
        assertEquals(-250.0D, dois.centerZ());

        BorderData.Settings tres = reloaded.get(MUNDO_TRES);
        assertNotNull(tres);
        assertEquals(1200.0D, tres.size(), "o segundo mundo nao pode herdar o tamanho do primeiro");
    }

    @Test
    void aWorldThatWasNeverAdjustedHasNothingSaved() {
        BorderData data = new BorderData();
        data.remember(MUNDO_DOIS, border(4000.0D, 0.0D, 0.0D));

        assertNull(BorderData.load(data.save(new CompoundTag(), null), null).get(MUNDO_TRES));
    }

    /**
     * Um arquivo com id de dimensao corrompido nao pode derrubar a carga dos outros: o mundo subiria
     * com todas as barreiras zeradas por causa de uma linha ruim.
     */
    @Test
    void aBrokenEntryIsSkippedWithoutTakingTheOthersDown() {
        BorderData data = new BorderData();
        data.remember(MUNDO_DOIS, border(4000.0D, 0.0D, 0.0D));

        CompoundTag saved = data.save(new CompoundTag(), null);
        CompoundTag broken = new CompoundTag();
        broken.putString("Dimension", "NAO :: E UM ID");
        saved.getList("Borders", net.minecraft.nbt.Tag.TAG_COMPOUND).add(broken);

        BorderData reloaded = BorderData.load(saved, null);

        assertNotNull(reloaded.get(MUNDO_DOIS), "a entrada boa tem que sobreviver a entrada ruim");
    }

    @Test
    void theInitialSettingsAreCenteredAtTheOriginWithTheConfiguredSize() {
        BorderData.Settings initial = BorderData.Settings.initial(10000.0D);

        assertEquals(10000.0D, initial.size());
        assertEquals(0.0D, initial.centerX());
        assertEquals(0.0D, initial.centerZ());
    }

    /** Aplicar e ler de volta tem que dar a mesma coisa, senao o que e salvo nao e o que se ve. */
    @Test
    void applyingSettingsToABorderRoundTrips() {
        BorderData.Settings settings = new BorderData.Settings(50.0D, -75.0D, 3000.0D, 0.35D, 7.0D, 12, 20);
        WorldBorder border = new WorldBorder();

        settings.applyTo(border);

        assertEquals(settings, BorderData.Settings.of(border));
    }

    private static WorldBorder border(double size, double centerX, double centerZ) {
        WorldBorder border = new WorldBorder();
        border.setCenter(centerX, centerZ);
        border.setSize(size);
        return border;
    }
}
