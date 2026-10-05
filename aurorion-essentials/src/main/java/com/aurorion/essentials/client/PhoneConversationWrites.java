package com.aurorion.essentials.client;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Uma fila de disco fora da thread do jogo, com o ultimo snapshot de cada personagem em memoria. */
final class PhoneConversationWrites {
    private record Pending(Path directory, PhoneConversationFile.Snapshot snapshot, byte[] photo,
                           Consumer<Exception> failure) {}

    private final Map<Path, Pending> pending = new ConcurrentHashMap<>();
    private final Set<Path> scheduled = ConcurrentHashMap.newKeySet();
    private final Map<Path, Object> directoryLocks = new ConcurrentHashMap<>();
    private final ScheduledExecutorService writer;
    private volatile boolean closing;

    PhoneConversationWrites() {
        this(new ScheduledThreadPoolExecutor(1, task -> {
            Thread thread = new Thread(task, "Aurorion phone persistence");
            thread.setDaemon(true);
            return thread;
        }));
        Runtime.getRuntime().addShutdownHook(new Thread(() -> drain(10_000), "Aurorion phone save on exit"));
    }

    PhoneConversationWrites(ScheduledExecutorService writer) {
        this.writer = writer;
        if (writer instanceof ScheduledThreadPoolExecutor scheduledWriter) {
            scheduledWriter.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        }
    }

    void snapshot(Path directory, Path file, PhoneConversationFile.Snapshot snapshot, Consumer<Exception> failure) {
        pending.put(file, new Pending(directory, snapshot, null, failure));
        schedule(file, 0);
    }

    void photo(Path directory, Path file, byte[] bytes, Consumer<Exception> failure) {
        pending.put(file, new Pending(directory, null, bytes.clone(), failure));
        schedule(file, 0);
    }

    PhoneConversationFile.Snapshot pendingSnapshot(Path file) {
        Pending value = pending.get(file);
        return value != null ? value.snapshot() : null;
    }

    byte[] pendingPhoto(Path file) {
        Pending value = pending.get(file);
        return value != null && value.photo() != null ? value.photo().clone() : null;
    }

    /** Um reset nao pode ser seguido por uma gravacao atrasada que ressuscite o celular anterior. */
    void reset(Path directory, Runnable wipe) {
        synchronized (directoryLock(directory)) {
            pending.entrySet().removeIf(entry -> entry.getValue().directory().equals(directory));
            wipe.run();
        }
    }

    /** O logout enfileira; o encerramento normal da JVM espera os arquivos antes de parar o daemon. */
    boolean drain(long timeoutMillis) {
        closing = true;
        writer.execute(() -> {
            for (Path file : Set.copyOf(pending.keySet())) write(file);
        });
        writer.shutdown();
        try {
            if (writer.awaitTermination(timeoutMillis, TimeUnit.MILLISECONDS)) return pending.isEmpty();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        writer.shutdownNow();
        return false;
    }

    private void schedule(Path file, long delayMillis) {
        if (closing) return;
        if (!scheduled.add(file)) return;
        writer.schedule(() -> write(file), delayMillis, TimeUnit.MILLISECONDS);
    }

    private void write(Path file) {
        boolean failed = false;
        try {
            Pending value = pending.get(file);
            if (value == null) return;
            synchronized (directoryLock(value.directory())) {
                if (pending.get(file) != value) return;
                if (value.snapshot() != null) PhoneConversationFile.write(file, value.snapshot());
                else writePhoto(file, value.photo());
                pending.remove(file, value);
            }
        } catch (IOException | RuntimeException failure) {
            failed = true;
            Pending value = pending.get(file);
            if (value != null) value.failure().accept(failure);
        } finally {
            scheduled.remove(file);
            // Uma nova mensagem durante a escrita substitui somente a proxima versao, nunca a ordem.
            if (!closing && pending.containsKey(file)) schedule(file, failed ? 5_000 : 0);
        }
    }

    private Object directoryLock(Path directory) {
        return directoryLocks.computeIfAbsent(directory, ignored -> new Object());
    }

    private static void writePhoto(Path file, byte[] photo) throws IOException {
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), "gram-photo-", ".tmp");
        try {
            Files.write(temporary, photo);
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
