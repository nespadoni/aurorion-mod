package com.aurorion.core.character;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class CharacterDataTest {
    @Test void accountAndCharacterHaveSeparateStableIdentities() {
        var data = new CharacterData();
        UUID account = UUID.randomUUID();
        var character = data.current(account);
        assertNotEquals(account, character.id());
        assertEquals(character, data.current(account));
        assertFalse(data.isDead(account));
        var restored = CharacterData.load(data.save(new CompoundTag(), null), null);
        assertEquals(character, restored.current(account));
    }

    @Test void deathIsTerminalAndSurvivesRestartWithoutAffectingAnotherAccount() {
        var data = new CharacterData();
        UUID account = UUID.randomUUID(), other = UUID.randomUUID();
        UUID original = data.current(account).id();
        assertTrue(data.markDead(account));
        long diedAt = data.current(account).diedAt();
        assertFalse(data.markDead(account));
        assertEquals(diedAt, data.current(account).diedAt());
        var restored = CharacterData.load(data.save(new CompoundTag(), null), null);
        assertTrue(restored.isDead(account));
        assertEquals(original, restored.current(account).id());
        assertFalse(restored.isDead(other));
    }
}
