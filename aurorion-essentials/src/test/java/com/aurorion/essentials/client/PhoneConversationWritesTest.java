package com.aurorion.essentials.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class PhoneConversationWritesTest {
    @TempDir Path directory;
    private final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
    private final PhoneConversationWrites writes = new PhoneConversationWrites(executor);

    @AfterEach
    void stopWriter() throws InterruptedException {
        executor.shutdownNow();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
    }

    @Test
    void reconnectSeesTheNewestQueuedSnapshotBeforeDiskIsReady() throws Exception {
        CountDownLatch release = blockWriter();
        Path file = directory.resolve("messages.properties");
        var old = history("old message");
        var newest = history("new message");
        writes.snapshot(directory, file, old, failure -> fail(failure));
        writes.snapshot(directory, file, newest, failure -> fail(failure));
        assertEquals(newest, writes.pendingSnapshot(file));
        assertFalse(Files.exists(file));
        release.countDown();
        executor.submit(() -> {}).get(5, TimeUnit.SECONDS);
        assertEquals(newest, PhoneConversationFile.read(file));
    }

    @Test
    void resetCancelsQueuedWritesSoTheOldCharacterCannotReappear() throws Exception {
        CountDownLatch release = blockWriter();
        Path file = directory.resolve("messages.properties");
        writes.snapshot(directory, file, history("old character"), failure -> fail(failure));
        writes.reset(directory, () -> assertFalse(Files.exists(file)));
        assertNull(writes.pendingSnapshot(file));
        release.countDown();
        executor.submit(() -> {}).get(5, TimeUnit.SECONDS);
        assertFalse(Files.exists(file));
    }

    @Test
    void failedSaveStaysInMemoryForReconnectAndDoesNotTouchAnotherCharacter() throws Exception {
        Path blocked = directory.resolve("blocked");
        Files.writeString(blocked, "not a directory");
        Path file = blocked.resolve("messages.properties");
        CountDownLatch failure = new CountDownLatch(1);
        var snapshot = history("unsaved message");
        writes.snapshot(blocked, file, snapshot, ignored -> failure.countDown());
        assertTrue(failure.await(5, TimeUnit.SECONDS));
        assertEquals(snapshot, writes.pendingSnapshot(file));
        writes.reset(blocked, () -> {});
        assertEquals("not a directory", Files.readString(blocked));
    }

    @Test
    void photoBytesAreCopiedBeforeTheNetworkBufferIsReused() throws Exception {
        CountDownLatch release = blockWriter();
        Path file = directory.resolve("photo.image");
        byte[] bytes = {1, 2, 3};
        writes.photo(directory, file, bytes, failure -> fail(failure));
        bytes[0] = 9;
        assertArrayEquals(new byte[]{1, 2, 3}, writes.pendingPhoto(file));
        release.countDown();
        executor.submit(() -> {}).get(5, TimeUnit.SECONDS);
        assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(file));
    }

    @Test
    void normalProcessExitDrainsTheLastLogoutSnapshotAndPhoto() throws Exception {
        Path history = directory.resolve("messages.properties");
        Path photo = directory.resolve("photo.image");
        var snapshot = history("last logout message");
        writes.snapshot(directory, history, snapshot, failure -> fail(failure));
        writes.photo(directory, photo, new byte[]{1, 2, 3}, failure -> fail(failure));
        assertTrue(writes.drain(5_000));
        assertEquals(snapshot, PhoneConversationFile.read(history));
        assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(photo));
    }

    private CountDownLatch blockWriter() throws InterruptedException {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        executor.execute(() -> {
            entered.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        });
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        return release;
    }

    private static PhoneConversationFile.Snapshot history(String message) {
        return new PhoneConversationFile.Snapshot(List.of(new PhoneConversationFile.ThreadState(Map.of("id", "other"),
                List.of(Map.of("text", message)), 0)), Set.of());
    }
}
