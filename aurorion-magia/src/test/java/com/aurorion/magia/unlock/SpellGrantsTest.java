package com.aurorion.magia.unlock;

import com.aurorion.magia.unlock.SpellGrants.GrantKind;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpellGrantsTest {
    private static final ResourceLocation CRUCIATUS = ResourceLocation.parse("aurorion_magia:dolor_cruciatus");
    private static final ResourceLocation HEARTSTOP = ResourceLocation.parse("irons_spellbooks:heartstop");
    private static final ResourceLocation BLOOD = ResourceLocation.parse("irons_spellbooks:blood");
    private static final ResourceLocation ENDER = ResourceLocation.parse("irons_spellbooks:ender");

    @Test
    void nadaLiberadoPorPadrao() {
        assertFalse(new SpellGrants().allows(CRUCIATUS, BLOOD));
        assertFalse(SpellGrants.EMPTY.allows(CRUCIATUS, BLOOD));
    }

    @Test
    void magiaAvulsaLiberaSoElaEmQualquerEscola() {
        SpellGrants grants = new SpellGrants();
        grants.grant(GrantKind.SPELL, CRUCIATUS);

        assertTrue(grants.allows(CRUCIATUS, BLOOD));
        // A staff pode mover a magia de escola pelo config do Iron's; a liberacao avulsa continua valendo.
        assertTrue(grants.allows(CRUCIATUS, ENDER));
        assertFalse(grants.allows(HEARTSTOP, BLOOD));
    }

    @Test
    void escolaLiberaTodasAsMagiasDela() {
        SpellGrants grants = new SpellGrants();
        grants.grant(GrantKind.SCHOOL, BLOOD);

        assertTrue(grants.allows(CRUCIATUS, BLOOD));
        assertTrue(grants.allows(HEARTSTOP, BLOOD));
        assertFalse(grants.allows(CRUCIATUS, ENDER));
        assertFalse(grants.allows(CRUCIATUS, null));
    }

    @Test
    void escolaNaoConcedeMagiaProibida() {
        ResourceLocation universus = ResourceLocation.parse("aurorion_magia:dolor_universus");
        SpellGrants grants = new SpellGrants();
        grants.grant(GrantKind.SCHOOL, BLOOD);

        assertTrue(grants.allows(universus, BLOOD));
        assertFalse(grants.allowsForbidden(universus));

        grants.grant(GrantKind.SPELL, universus);
        assertTrue(grants.allowsForbidden(universus));
    }

    @Test
    void grantERevokeDizemSeMudaram() {
        SpellGrants grants = new SpellGrants();

        assertTrue(grants.grant(GrantKind.SCHOOL, BLOOD));
        assertFalse(grants.grant(GrantKind.SCHOOL, BLOOD));
        assertTrue(grants.revoke(GrantKind.SCHOOL, BLOOD));
        assertFalse(grants.revoke(GrantKind.SCHOOL, BLOOD));
        assertTrue(grants.isEmpty());
    }

    @Test
    void emptyCompartilhadoNaoAceitaMutacao() {
        assertThrows(UnsupportedOperationException.class, () -> SpellGrants.EMPTY.grant(GrantKind.SPELL, CRUCIATUS));
        assertTrue(SpellGrants.EMPTY.view(GrantKind.SPELL).isEmpty());
    }

    @Test
    void sobreviveAoSaveEDescartaIdIlegivel() {
        SpellGrants original = new SpellGrants();
        original.grant(GrantKind.SPELL, CRUCIATUS);
        original.grant(GrantKind.SCHOOL, BLOOD);

        CompoundTag entry = new CompoundTag();
        original.write(entry);
        ((ListTag) entry.get("Spells")).add(StringTag.valueOf("Id Invalido!"));

        SpellGrants loaded = SpellGrants.read(entry);
        assertEquals(original.view(GrantKind.SPELL), loaded.view(GrantKind.SPELL));
        assertEquals(original.view(GrantKind.SCHOOL), loaded.view(GrantKind.SCHOOL));
    }
}
