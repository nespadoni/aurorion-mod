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

    @Test void multipleAltsPerAccountAndNoAltOfAnAlt() {
        AltData data = new AltData();
        UUID owner = UUID.randomUUID();
        var alt = data.create(owner, "Neto_alt");

        var second = data.create(owner, "Neto_a2");
        assertNotEquals(alt.altId(), second.altId());
        assertEquals(2, data.forOwner(owner).size());
        assertThrows(IllegalStateException.class, () -> data.create(alt.altId(), "Neto_a3"));
        assertThrows(IllegalArgumentException.class, () -> data.create(UUID.randomUUID(), "neto_alt"));
    }

    @Test void selectedAltSurvivesRestartAndCannotBelongToAnotherOwner() {
        AltData data = new AltData();
        UUID owner = UUID.randomUUID();
        var first = data.create(owner, "Neto_alt");
        var second = data.create(owner, "Neto_a2");
        var foreign = data.create(UUID.randomUUID(), "Outro_alt");
        data.select(owner, first.altId());
        data.select(owner, second.altId());
        assertThrows(IllegalArgumentException.class, () -> data.select(owner, foreign.altId()));

        AltData restored = AltData.load(data.save(new CompoundTag(), null), null);
        assertEquals(2, restored.forOwner(owner).size());
        assertEquals(second.altId(), restored.byOwner(owner).altId());
        assertFalse(restored.find(first.altId()).active());
        restored.select(owner, null);
        assertTrue(restored.forOwner(owner).stream().noneMatch(AltData.Alt::active));
    }

    @Test void legacySavePreservesIdentityAndSelectionWhenAddingAlts() {
        UUID owner = UUID.randomUUID();
        UUID legacyId = AltData.altIdOf(owner);
        CompoundTag entry = new CompoundTag();
        entry.putUUID("Player", owner);
        entry.putUUID("AltId", legacyId);
        entry.putString("AltName", "Neto_alt");
        entry.putBoolean("Active", true);
        var list = new net.minecraft.nbt.ListTag();
        list.add(entry);
        CompoundTag tag = new CompoundTag();
        tag.put("Alts", list);

        AltData loaded = AltData.load(tag, null);
        loaded.create(owner, "Neto_a2");
        AltData restored = AltData.load(loaded.save(new CompoundTag(), null), null);
        assertEquals(legacyId, restored.byOwner(owner).altId());
        assertEquals(owner, restored.ownerOf(legacyId));
        assertTrue(restored.find(legacyId).active());
        assertEquals(2, restored.forOwner(owner).size());
    }

    @Test void removingAnAltKeepsTheOtherIdentitiesAndSelection() {
        AltData data = new AltData();
        UUID owner = UUID.randomUUID();
        var first = data.create(owner, "Neto_alt");
        var second = data.create(owner, "Neto_a2");
        data.select(owner, second.altId());
        data.remove(first.altId());
        assertEquals(second.altId(), data.byOwner(owner).altId());
        assertTrue(data.byOwner(owner).active());
        assertFalse(data.isAlt(first.altId()));
        data.remove(second.altId());
        assertNull(data.byOwner(owner));
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
