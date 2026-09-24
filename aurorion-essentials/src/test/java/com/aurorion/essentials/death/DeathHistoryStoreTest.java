package com.aurorion.essentials.death;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DeathHistoryStoreTest {
    @TempDir Path root;

    @Test void retentionOnlyRemovesOldDeathsOfTheSameAccount() throws IOException {
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        CompoundTag oldest = snapshot(first), other = snapshot(second), newest = snapshot(first);
        try (DeathHistoryStore store = new DeathHistoryStore(root)) {
            store.saveDeath(oldest, 1); store.saveDeath(other, 1); store.saveDeath(newest, 1);
            assertEquals(newest.getUUID("Id"), store.list(first).getCompound(0).getUUID("Id"));
            assertEquals(other, store.read(other.getUUID("Id")));
            assertFalse(Files.exists(root.resolve("records").resolve(oldest.getUUID("Id") + ".nbt")));
        }
    }

    @Test void reservationSurvivesRestartAndBackupCanBeOpenedById() throws IOException {
        UUID id = UUID.randomUUID(), target = UUID.randomUUID();
        CompoundTag backup = snapshot(target);
        try (DeathHistoryStore store = new DeathHistoryStore(root)) { store.reserve(id, 3, backup, "Admin", target); }
        try (DeathHistoryStore store = new DeathHistoryStore(root)) {
            assertThrows(IOException.class, () -> store.reserve(id, 3, backup, "Admin", target));
            assertThrows(IOException.class, () -> store.reserve(id, -1, backup, "Admin", target));
            assertEquals(backup, store.read(backup.getUUID("Id")));
            assertDoesNotThrow(() -> store.reserve(id, 4, snapshot(target), "Admin", target));
        }
    }

    @Test void fullRestoreBlocksEveryLaterPartialRestoreEvenAfterCompletion() throws IOException {
        UUID id = UUID.randomUUID(), target = UUID.randomUUID();
        try (DeathHistoryStore store = new DeathHistoryStore(root)) {
            store.reserve(id, -1, snapshot(target), "Admin", target);
            store.finish(id, -1, "APPLIED");
            assertThrows(IOException.class, () -> store.reserve(id, 0, snapshot(target), "Admin", target));
        }
    }

    @Test void failedBackupCannotReserveOrChangeTheDeath() throws IOException {
        UUID id = UUID.randomUUID(), target = UUID.randomUUID();
        Files.writeString(root.resolve("backups"), "not a directory");
        try (DeathHistoryStore store = new DeathHistoryStore(root)) {
            assertThrows(IOException.class, () -> store.reserve(id, -1, snapshot(target), "Admin", target));
            assertFalse(Files.exists(root.resolve("restores").resolve(id + ".nbt")));
        }
    }

    /**
     * O indice de nomes: e o que permite a staff consultar por "Bella Noob" em vez da UUID da conta,
     * inclusive semanas depois e com a pessoa offline.
     */
    @Test void deathsCanBeFoundByAnyNameTheAccountDiedUnder() throws IOException {
        UUID owner = UUID.randomUUID();
        CompoundTag death = snapshot(owner);
        death.putString("FakeNamePlain", "Bella Noob");
        death.putString("CharacterName", "Bellatrix de Aurorion");
        try (DeathHistoryStore store = new DeathHistoryStore(root)) {
            store.saveDeath(death, 10);
            assertEquals(owner, store.findByName("Bella Noob"));
            assertEquals(owner, store.findByName("  bella noob "));
            assertEquals(owner, store.findByName("Bellatrix de Aurorion"));
            // O nick da conta continua valendo: e o unico nome de quem nunca usou /fakename.
            assertEquals(owner, store.findByName("Player"));
            assertNull(store.findByName("Fulano"));
            assertTrue(store.knownNames().contains("Bella Noob"));
        }
    }

    /** Trocar de nome nao apaga o passado: os dois nomes continuam achando a mesma conta. */
    @Test void anOldNameStillFindsTheAccountAfterTheNameChanges() throws IOException {
        UUID owner = UUID.randomUUID();
        CompoundTag before = snapshot(owner);
        before.putString("FakeNamePlain", "Bella Noob");
        CompoundTag after = snapshot(owner);
        after.putString("FakeNamePlain", "Bella Veterana");
        after.putLong("Time", 9999);
        try (DeathHistoryStore store = new DeathHistoryStore(root)) {
            store.saveDeath(before, 10);
            store.saveDeath(after, 10);
            assertEquals(owner, store.findByName("Bella Noob"));
            assertEquals(owner, store.findByName("Bella Veterana"));
        }
    }

    private static CompoundTag snapshot(UUID owner) {
        CompoundTag snapshot = new CompoundTag();
        snapshot.putInt("Format", 1); snapshot.putUUID("Id", UUID.randomUUID()); snapshot.putUUID("Owner", owner);
        snapshot.putString("Name", "Player"); snapshot.putLong("Time", 1234);
        snapshot.putString("Cause", "Test death"); snapshot.putString("Dimension", "minecraft:overworld");
        snapshot.putDouble("X", 1); snapshot.putDouble("Y", 64); snapshot.putDouble("Z", 3);
        snapshot.putBoolean("CuriosComplete", true);
        return snapshot;
    }
}
