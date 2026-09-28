package com.aurorion.core.character;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AltDataTest {
    /** O alt tem de ser sempre o mesmo jogador: o UUID e a chave de toda a progressao dele. */
    @Test void altIdIsStableAndDifferentFromTheAccount() {
        UUID owner = UUID.randomUUID();
        assertEquals(AltData.altIdOf(owner), AltData.altIdOf(owner));
        assertNotEquals(owner, AltData.altIdOf(owner));
        assertNotEquals(AltData.altIdOf(owner), AltData.altIdOf(UUID.randomUUID()));
    }

    @Test void profileNameFitsTheVanillaLimit() {
        assertEquals("NetoSpadoni_alt", AltData.altNameOf("NetoSpadoni", 0));
        assertTrue(AltData.altNameOf("UmNomeMuitoComprido", 0).length() <= AltData.MAX_PROFILE_NAME);
        assertTrue(AltData.altNameOf("UmNomeMuitoComprido", 3).endsWith("_a4"));
        assertTrue(AltData.altNameOf("UmNomeMuitoComprido", 3).length() <= AltData.MAX_PROFILE_NAME);
    }

    @Test void createToggleAndSurviveRestart() {
        AltData data = new AltData();
        UUID owner = UUID.randomUUID();
        var alt = data.create(owner, "Neto_alt");

        assertFalse(alt.active());
        assertTrue(data.isAlt(alt.altId()));
        assertEquals(owner, data.ownerOf(alt.altId()));
        assertTrue(data.setActive(owner, true).active());

        AltData restored = AltData.load(data.save(new CompoundTag(), null), null);
        assertTrue(restored.byOwner(owner).active());
        assertEquals(owner, restored.ownerOf(alt.altId()));
        assertTrue(restored.nameTaken("neto_ALT"));
    }

    @Test void oneAltPerAccountAndNoAltOfAnAlt() {
        AltData data = new AltData();
        UUID owner = UUID.randomUUID();
        var alt = data.create(owner, "Neto_alt");

        assertThrows(IllegalStateException.class, () -> data.create(owner, "Neto_a2"));
        assertThrows(IllegalStateException.class, () -> data.create(alt.altId(), "Neto_a3"));
        assertThrows(IllegalArgumentException.class, () -> data.create(UUID.randomUUID(), "neto_alt"));
    }

    /** Remover solta o vinculo; recriar devolve o mesmo UUID, e com ele o mesmo personagem. */
    @Test void removeKeepsTheSameIdentityForLater() {
        AltData data = new AltData();
        UUID owner = UUID.randomUUID();
        UUID first = data.create(owner, "Neto_alt").altId();

        assertNotNull(data.remove(owner));
        assertFalse(data.isAlt(first));
        assertEquals(first, data.create(owner, "Neto_alt").altId());
    }
}
