package com.aurorion.essentials.death;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
        rememberNames(snapshot, owner);
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

    // --- Indice de nomes ------------------------------------------------------------------------
    //
    // Consultar o historico pela UUID da conta e inviavel no dia a dia: ninguem decora UUID, e num
    // servidor de RP a staff nem sempre sabe o nick da Mojang de quem morreu — sabe o nome do
    // personagem. Este indice guarda "nome usado numa morte -> conta", para que /deathhistory aceite
    // exatamente o nome que a staff leu no chat, inclusive de quem esta offline ou ja trocou de nome.
    //
    // Um arquivo so, reescrito junto com a morte (dezenas de entradas, uns poucos KB). A mesma fila
    // de IO da morte, entao nada disso toca a thread do servidor.

    private static final String NAMES = "names.nbt";
    private static final int MAX_NAMES = 4096;
    private static final String KEY_OWNER = "Owner";
    private static final String KEY_DISPLAY = "Display";
    private static final String KEY_TIME = "Time";

    /** A conta que morreu usando este nome, ou {@code null}. Compara sem cor e sem caixa. */
    @Nullable
    public UUID findByName(String name) throws IOException {
        CompoundTag index = names();
        String key = nameKey(name);
        return index.contains(key) ? index.getCompound(key).getUUID(KEY_OWNER) : null;
    }

    /** Os nomes legiveis ja vistos em alguma morte, para sugerir no Tab. */
    public List<String> knownNames() throws IOException {
        CompoundTag index = names();
        List<String> names = new ArrayList<>(index.size());
        for (String key : index.getAllKeys()) names.add(index.getCompound(key).getString(KEY_DISPLAY));
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    /**
     * Grava os nomes desta morte no indice. Entram o nome do personagem (quando houver), o nome falso
     * e o nick da Mojang: a staff pode procurar por qualquer um dos tres.
     *
     * <p>Nome repetido aponta para a conta da morte mais recente. Duas pessoas com o mesmo nome em
     * epocas diferentes e raro (o {@code /fakename} recusa colisao entre quem esta online) e, quando
     * acontece, a consulta ainda mostra de quem e a conta encontrada.
     *
     * <p>Teto de {@value #MAX_NAMES} nomes, podando os mais antigos. Sem ele o indice cresceria para
     * sempre: {@code /fakename} e livre, e cada morte com nome novo deixaria mais uma entrada aqui.
     */
    private void rememberNames(CompoundTag snapshot, UUID owner) throws IOException {
        CompoundTag index = names();
        long time = snapshot.getLong(KEY_TIME);
        boolean changed = false;
        for (String field : new String[]{"CharacterName", "FakeNamePlain", "Name"}) {
            if (!snapshot.contains(field)) continue;
            String display = snapshot.getString(field).trim();
            String key = nameKey(display);
            if (key.isEmpty()) continue;
            // Nome ja apontado por uma morte igual ou mais recente fica como esta: a morte mais nova
            // e a que manda, venha ela de quem vier.
            if (index.contains(key) && index.getCompound(key).getLong(KEY_TIME) >= time) continue;
            CompoundTag entry = new CompoundTag();
            entry.putUUID(KEY_OWNER, owner);
            entry.putString(KEY_DISPLAY, display);
            entry.putLong(KEY_TIME, time);
            index.put(key, entry);
            changed = true;
        }
        if (!changed) return;
        while (index.size() > MAX_NAMES) index.remove(oldestName(index));
        write(root.resolve(NAMES), index);
    }

    private static String oldestName(CompoundTag index) {
        String oldest = null;
        long when = Long.MAX_VALUE;
        for (String key : index.getAllKeys()) {
            long time = index.getCompound(key).getLong(KEY_TIME);
            if (time < when) { when = time; oldest = key; }
        }
        return oldest;
    }

    private CompoundTag names() throws IOException {
        Path path = root.resolve(NAMES);
        return Files.exists(path) ? read(path) : new CompoundTag();
    }

    /** A chave de comparacao: o nome como o olho le, sem caixa. As cores ja saem no snapshot. */
    private static String nameKey(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
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
        // O nome usado na hora da morte, para a linha da lista mostrar quem era sem abrir o registro.
        if (snapshot.contains("FakeName")) summary.putString("FakeName", snapshot.getString("FakeName"));
        if (snapshot.contains("FakeNamePlain")) summary.putString("FakeNamePlain", snapshot.getString("FakeNamePlain"));
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
