package com.aurorion.essentials.death;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicLong;

/** One bounded IO queue per server. Only detached NBT crosses this boundary. */
public final class DeathHistoryStore implements AutoCloseable {
    private final Path root;
    private final AtomicLong queuedBytes = new AtomicLong();
    private static final long MAX_QUEUED_BYTES = 64L * 1024 * 1024;
    private final ThreadPoolExecutor io = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(128), runnable -> new Thread(runnable, "aurorion-death-history"),
            new ThreadPoolExecutor.AbortPolicy());

    public DeathHistoryStore(MinecraftServer server) {
        this(server.getWorldPath(LevelResource.ROOT).resolve("aurorion/death-history"));
    }

    DeathHistoryStore(Path root) { this.root = root; }

    public void submit(Runnable task) { io.execute(task); }

    public void submitSnapshot(CompoundTag snapshot, Runnable task) {
        long bytes = snapshot.sizeInBytes();
        if (queuedBytes.addAndGet(bytes) > MAX_QUEUED_BYTES) {
            queuedBytes.addAndGet(-bytes);
            throw new RejectedExecutionException("Death history exceeded 64 MiB queued snapshots");
        }
        try {
            io.execute(() -> {
                try { task.run(); }
                finally { queuedBytes.addAndGet(-bytes); }
            });
        } catch (RuntimeException e) { queuedBytes.addAndGet(-bytes); throw e; }
    }

    public void saveDeath(CompoundTag snapshot, int retention) throws IOException {
        UUID id = snapshot.getUUID("Id");
        write(root.resolve("records").resolve(id + ".nbt"), snapshot);
        UUID owner = snapshot.getUUID("Owner");
        ListTag index = list(owner);
        CompoundTag summary = summary(snapshot);
        index.add(0, summary);
        // Publish the new index before pruning records. A crash may leave an orphan, never a dangling new entry.
        ListTag removed = new ListTag();
        while (index.size() > retention) removed.add(index.remove(index.size() - 1));
        CompoundTag file = new CompoundTag(); file.put("Deaths", index);
        write(root.resolve("players").resolve(owner + ".nbt"), file);
        for (Tag old : removed) {
            UUID oldId = ((CompoundTag) old).getUUID("Id");
            Files.deleteIfExists(root.resolve("records").resolve(oldId + ".nbt"));
        }
    }

    public ListTag list(UUID owner) throws IOException {
        Path path = root.resolve("players").resolve(owner + ".nbt");
        return Files.exists(path) ? read(path).getList("Deaths", Tag.TAG_COMPOUND) : new ListTag();
    }

    public CompoundTag read(UUID id) throws IOException {
        Path record = root.resolve("records").resolve(id + ".nbt");
        if (!Files.exists(record)) record = root.resolve("backups").resolve(id + ".nbt");
        CompoundTag snapshot = read(record);
        if (snapshot.getInt("Format") != 1 || !snapshot.getUUID("Id").equals(id)) {
            throw new IOException("Formato de snapshot desconhecido.");
        }
        return snapshot;
    }

    /** Reserve before mutation: repeats and a crash in the commit window cannot silently duplicate items. */
    public void reserve(UUID id, int item, CompoundTag backup, String actor, UUID target) throws IOException {
        Path journalPath = root.resolve("restores").resolve(id + ".nbt");
        CompoundTag journal = Files.exists(journalPath) ? read(journalPath) : new CompoundTag();
        String key = item < 0 ? "full" : "item_" + item;
        if (journal.contains("full") || journal.contains(key) || (item < 0 && !journal.isEmpty())) {
            throw new IOException("Esta recuperacao ja foi reservada/executada. Consulte a auditoria; nao sera repetida.");
        }
        write(root.resolve("backups").resolve(backup.getUUID("Id") + ".nbt"), backup);
        CompoundTag receipt = new CompoundTag();
        receipt.putString("Status", "RESERVED"); receipt.putString("Admin", actor);
        receipt.putUUID("Target", target); receipt.putUUID("Backup", backup.getUUID("Id"));
        receipt.putLong("Time", System.currentTimeMillis());
        journal.put(key, receipt);
        write(journalPath, journal);
    }

    public void finish(UUID id, int item, String status) throws IOException {
        Path path = root.resolve("restores").resolve(id + ".nbt");
        CompoundTag journal = read(path);
        journal.getCompound(item < 0 ? "full" : "item_" + item).putString("Status", status);
        write(path, journal);
    }

    private static CompoundTag summary(CompoundTag snapshot) {
        CompoundTag summary = new CompoundTag();
        for (String key : new String[]{"Id", "Owner", "Name", "Time", "Cause", "Dimension", "X", "Y", "Z", "CuriosComplete"}) {
            summary.put(key, snapshot.get(key).copy());
        }
        if (snapshot.contains("CaptureError")) summary.putString("CaptureError", snapshot.getString("CaptureError"));
        // Opcionais: snapshots anteriores ao personagem no registro nao tem estes campos.
        if (snapshot.hasUUID("Character")) summary.putUUID("Character", snapshot.getUUID("Character"));
        if (snapshot.contains("CharacterName")) summary.putString("CharacterName", snapshot.getString("CharacterName"));
        return summary;
    }

    private static CompoundTag read(Path path) throws IOException {
        return NbtIo.readCompressed(path, NbtAccounter.create(32L * 1024 * 1024));
    }

    private static void write(Path path, CompoundTag data) throws IOException {
        Files.createDirectories(path.getParent());
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        try {
            NbtIo.writeCompressed(data, temporary);
            try (FileChannel file = FileChannel.open(temporary, StandardOpenOption.WRITE)) { file.force(true); }
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
    }

    @Override public void close() {
        io.shutdown();
        try {
            if (!io.awaitTermination(30, TimeUnit.SECONDS)) {
                com.aurorion.essentials.AurorionEssentials.LOGGER.error("Death history IO still draining at server stop.");
            }
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
