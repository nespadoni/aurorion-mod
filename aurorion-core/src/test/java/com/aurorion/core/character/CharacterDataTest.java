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

    /** Quem ja jogava so ganha um nome: mesmo ID, mesma progressao, nada de reset. */
    @Test void namingALivingCharacterKeepsItsIdentity() {
        var data = new CharacterData();
        UUID account = UUID.randomUUID();
        UUID original = data.current(account).id();

        assertTrue(data.needsName(account));
        var named = data.nameLiving(account, new CharacterName("Alda", "Verrine"));

        assertEquals(original, named.id());
        assertEquals("Alda Verrine", named.fullName());
        assertFalse(data.needsName(account));
        assertThrows(IllegalStateException.class,
                () -> data.nameLiving(account, new CharacterName("Outra", "Pessoa")));
    }

    @Test void aNameBelongsToOneCharacterEvenAfterItDies() {
        var data = new CharacterData();
        UUID account = UUID.randomUUID(), other = UUID.randomUUID();
        data.current(account);
        data.nameLiving(account, new CharacterName("Alda", "Verrine"));
        data.markDead(account);

        data.current(other);
        assertFalse(data.nameAvailable(new CharacterName("alda", "VERRINE")));
        assertThrows(IllegalStateException.class,
                () -> data.nameLiving(other, new CharacterName("Alda", "Verrine")));
    }

    /**
     * O caso que a transacao existe para cobrir: a reserva sobrevive a um reinicio no meio do reset,
     * e a conta continua morta ate a publicacao acontecer.
     */
    @Test void anInterruptedReplacementResumesFromDiskStillDead() {
        var data = new CharacterData();
        UUID account = UUID.randomUUID();
        data.current(account);
        data.nameLiving(account, new CharacterName("Alda", "Verrine"));
        UUID first = data.current(account).id();
        data.markDead(account);

        var transaction = data.beginReplacement(account, "conta", new CharacterName("Brun", "Solaz"));
        var restored = CharacterData.load(data.save(new CompoundTag(), null), null);

        assertTrue(restored.isDead(account), "a conta so volta a viver quando a troca termina");
        assertEquals(first, restored.current(account).id());
        assertNotNull(restored.pending(account));
        assertEquals(transaction.next().id(), restored.pending(account).next().id());
        assertFalse(restored.nameAvailable(new CharacterName("Brun", "Solaz")),
                "o nome reservado nao pode ser tomado por outra pessoa no meio da troca");

        var published = restored.finishReplacement(account, transaction.next().id());
        assertFalse(restored.isDead(account));
        assertNotEquals(first, published.id());
        assertEquals("Brun Solaz", published.fullName());
        assertNull(restored.pending(account));
        assertTrue(restored.history().containsKey(first));
    }

    @Test void publishingRefusesAStaleTransaction() {
        var data = new CharacterData();
        UUID account = UUID.randomUUID();
        data.current(account);
        data.markDead(account);
        data.beginReplacement(account, "conta", new CharacterName("Brun", "Solaz"));

        assertThrows(IllegalStateException.class, () -> data.finishReplacement(account, UUID.randomUUID()));
    }

    @Test void abandoningAReservationFreesTheNameAndKeepsTheAccountDead() {
        var data = new CharacterData();
        UUID account = UUID.randomUUID();
        data.current(account);
        data.markDead(account);
        data.beginReplacement(account, "conta", new CharacterName("Brun", "Solaz"));

        assertNotNull(data.abandonReplacement(account));
        assertNull(data.pending(account));
        assertTrue(data.isDead(account), "cancelar uma reserva nao ressuscita ninguem");
        assertTrue(data.nameAvailable(new CharacterName("Brun", "Solaz")));
        assertNull(data.abandonReplacement(account));
    }

    @Test void renamingReleasesThePreviousName() {
        var data = new CharacterData();
        UUID account = UUID.randomUUID();
        data.current(account);
        data.nameLiving(account, new CharacterName("Alda", "Verrine"));
        UUID id = data.current(account).id();

        var renamed = data.rename(account, new CharacterName("Alda", "Verrinne"));

        assertEquals(id, renamed.id());
        assertTrue(data.nameAvailable(new CharacterName("Alda", "Verrine")));
        assertFalse(data.nameAvailable(new CharacterName("Alda", "Verrinne")));
        assertEquals(renamed, data.rename(account, new CharacterName("Alda", "Verrinne")),
                "corrigir para o mesmo nome nao pode colidir com o proprio personagem");
    }
}
