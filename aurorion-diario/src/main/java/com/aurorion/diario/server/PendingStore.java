package com.aurorion.diario.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;

/**
 * Rascunhos do diário que o servidor do jogo guardou porque o site não respondeu.
 *
 * <p>É o estado "salvo no servidor" da proposta: a pessoa continua escrevendo, o texto fica em
 * {@code <mundo>/aurorion_diario/pendentes.json}, e uma rotina tenta mandar ao site de tempos em
 * tempos. Só a versão mais recente de cada sessão de edição importa — uma nova gravação da mesma
 * sessão substitui a anterior. O servidor do jogo não é um segundo banco: assim que o site aceita,
 * a cópia daqui some.
 *
 * <p>As chaves vêm do cliente, então ficam sempre sob o perfil de quem gravou ({@link #key}).
 * {@link #put} só confirma depois da escrita atômica em disco; um arquivo ilegível nunca é
 * sobrescrito (é renomeado e fica para recuperação manual), e um registro ruim vai para quarentena
 * sem levar os outros junto ({@link #load}). Sem dependência do Minecraft: a escrita roda no
 * {@link Executor} recebido, fora da thread do servidor.
 */
public final class PendingStore {
    static final int MAX_TOTAL = 300;
    static final int MAX_PER_PROFILE = 25;
    private static final long MAX_FILE_BYTES = 32L * 1024 * 1024;
    /**
     * Documento de um rascunho, em caracteres do JSON. O texto do jogo tem ate 10 mil caracteres; mesmo
     * picotado em trechos de negrito e italico, o documento em blocos cabe aqui.
     */
    static final int MAX_DOCUMENT_CHARS = 128 * 1024;
    /** Soma dos documentos guardados: no pior caso de UTF-8 (3 bytes) ainda abaixo do arquivo maximo. */
    static final long MAX_TOTAL_DOCUMENT_CHARS = 8L * 1024 * 1024;
    static final int MAX_FIELD_CHARS = 512;

    public record Pending(String draftKey, String operationId, long entryId, int baseVersion,
                          String title, String loreDate, String document,
                          String profile, String characterId, String characterName,
                          long savedAt, boolean conflict, String accountProfile) {
        public Pending(String draftKey, String operationId, long entryId, int baseVersion,
                       String title, String loreDate, String document, String profile,
                       String characterId, String characterName, long savedAt, boolean conflict) {
            this(draftKey, operationId, entryId, baseVersion, title, loreDate, document,
                    profile, characterId, characterName, savedAt, conflict, profile);
        }
        Pending withBase(long id, int version, boolean conflicted) {
            return new Pending(draftKey, operationId, id, version, title, loreDate, document,
                    profile, characterId, characterName, savedAt, conflicted, accountProfile);
        }
    }

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private final Path file;
    private final Executor io;
    private final Consumer<String> warn;
    private final Map<String, Pending> byKey = new LinkedHashMap<>();
    private final List<CompletableFuture<Boolean>> acknowledgements = new ArrayList<>();
    private int flushingAcknowledgements;
    private boolean writing;
    private boolean readable = true;
    private boolean lastWriteSucceeded;
    private long lastWriteAt;

    public record Diagnostics(int total, int retryable, int conflicts, long oldestSavedAt,
                              boolean readable, boolean writing, int waitingForDisk,
                              boolean lastWriteSucceeded, long lastWriteAt) { }

    /** Aggregate only: no text, author, draft key or disk access is exposed. */
    public synchronized Diagnostics diagnostics() {
        int conflicts = 0;
        long oldest = 0;
        for (Pending pending : byKey.values()) {
            if (pending.conflict()) conflicts++;
            if (pending.savedAt() > 0 && (oldest == 0 || pending.savedAt() < oldest)) oldest = pending.savedAt();
        }
        return new Diagnostics(byKey.size(), byKey.size() - conflicts, conflicts, oldest, readable,
                writing, acknowledgements.size() + flushingAcknowledgements, lastWriteSucceeded, lastWriteAt);
    }

    public PendingStore(Path file, Executor io, Consumer<String> warn) {
        this.file = file;
        this.io = io;
        this.warn = warn;
        load();
    }

    /** A chave escolhida pelo cliente nunca compartilha o namespace de outro jogador. */
    public static String key(String profile, String draftKey) {
        return profile + "/" + draftKey;
    }

    public synchronized CompletableFuture<Boolean> put(Pending pending) {
        if (!fits(pending)) return CompletableFuture.completedFuture(false);
        String key = key(pending.profile(), pending.draftKey());
        long total = pending.document().length();
        for (var entry : byKey.entrySet()) {
            if (!entry.getKey().equals(key)) total += entry.getValue().document().length();
        }
        if (total > MAX_TOTAL_DOCUMENT_CHARS) return CompletableFuture.completedFuture(false);
        if (!byKey.containsKey(key)) {
            long sameProfile = byKey.values().stream().filter(p -> p.profile().equals(pending.profile())).count();
            if (byKey.size() >= MAX_TOTAL || sameProfile >= MAX_PER_PROFILE) return CompletableFuture.completedFuture(false);
        }
        byKey.put(key, pending);
        return persist();
    }

    public synchronized Optional<Pending> get(String profile, String draftKey) {
        return Optional.ofNullable(byKey.get(key(profile, draftKey)));
    }

    public synchronized void remove(String profile, String draftKey) {
        if (byKey.remove(key(profile, draftKey)) != null) persist();
    }

    /**
     * O site confirmou {@code sent}. A copia guardada da mesma sessao some se for ela ou se for mais
     * antiga (uma gravacao mais nova ja chegou ao site); se for mais nova, foi digitada enquanto a
     * rede estava ocupada e passa a valer sobre a versao confirmada. Um ACK antigo nunca apaga texto.
     */
    public synchronized boolean acknowledge(Pending sent, long entryId, int version) {
        String key = key(sent.profile(), sent.draftKey());
        Pending current = byKey.get(key);
        if (current == null) return false;
        if (current.operationId().equals(sent.operationId()) || current.savedAt() < sent.savedAt()) {
            byKey.remove(key);
            persist();
            return true;
        }
        if (current.baseVersion() == sent.baseVersion() && current.entryId() == sent.entryId()) {
            byKey.put(key, current.withBase(entryId, version, false));
            persist();
        }
        return false;
    }

    /**
     * A pessoa ficou com a versão do site: as cópias recusadas por conflito deixam de valer (o site
     * já guardou cada uma no histórico da entrada). Cópia ainda na fila nunca é descartada.
     */
    public synchronized void discardConflicts(String profile, long entryId) {
        if (entryId <= 0) return;
        if (byKey.values().removeIf(p -> p.conflict() && p.profile().equals(profile) && p.entryId() == entryId)) persist();
    }

    public synchronized void markConflict(Pending sent, long entryId) {
        String key = key(sent.profile(), sent.draftKey());
        Pending current = byKey.get(key);
        if (current != null && current.operationId().equals(sent.operationId())) {
            byKey.put(key, current.withBase(entryId > 0 ? entryId : current.entryId(), current.baseVersion(), true));
            persist();
        }
    }

    public synchronized List<Pending> retryable(int limit) {
        if (limit <= 0) return List.of();
        return byKey.values().stream().filter(p -> !p.conflict()).limit(limit).toList();
    }

    public synchronized Optional<Pending> forEntry(String profile, long entryId) {
        if (entryId <= 0) return Optional.empty();
        return byKey.values().stream().filter(p -> p.profile().equals(profile) && p.entryId() == entryId)
                .reduce((first, second) -> second.savedAt() >= first.savedAt() ? second : first);
    }

    public synchronized List<Pending> forProfile(String profile) {
        return byKey.values().stream().filter(p -> p.profile().equals(profile)).toList();
    }

    public synchronized int size() {
        return byKey.size();
    }

    /**
     * Le o arquivo registro a registro.
     *
     * <p>Antes, um registro ruim (ou um arquivo grande demais) deixava o arquivo inteiro ilegivel, e
     * com ele o modo offline do diario de <b>todo mundo</b> desligado ate alguem mexer a mao. Agora so o
     * que esta ruim sai do caminho: registros invalidos vao para um arquivo de quarentena ao lado
     * ({@code pendentes.json.rejeitados-<hora>.json}); um arquivo grande demais ou que nem e JSON e
     * renomeado inteiro ({@code .ilegivel-<hora>}) e o diario recomeca vazio. Nada e apagado: tudo fica
     * para recuperacao manual. So se nem renomear der certo o arquivo continua no lugar e o modo offline
     * fica desligado, como antes — sobrescrever o que nao foi lido nunca e uma opcao.
     */
    private void load() {
        if (!Files.isRegularFile(file)) return;
        JsonArray items;
        try {
            if (Files.size(file) > MAX_FILE_BYTES) throw new IOException("oversized");
            JsonElement root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            if (!root.isJsonArray()) throw new IOException("not_a_list");
            items = root.getAsJsonArray();
        } catch (IOException | RuntimeException e) {
            setAside(e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage()));
            return;
        }
        JsonArray rejected = new JsonArray();
        for (JsonElement element : items) {
            Pending p = parse(element);
            if (p == null) {
                rejected.add(element);
                continue;
            }
            byKey.put(key(p.profile(), p.draftKey()), p);
        }
        if (rejected.isEmpty()) return;
        Path quarantine = file.resolveSibling(file.getFileName() + ".rejeitados-" + System.currentTimeMillis() + ".json");
        try {
            Files.writeString(quarantine, GSON.toJson(rejected), StandardCharsets.UTF_8);
            warn.accept("Diario: " + rejected.size() + " rascunho(s) guardado(s) invalido(s) separado(s) em " + quarantine.getFileName());
        } catch (IOException | RuntimeException e) {
            // Sem a quarentena, regravar o arquivo perderia os registros ruins: preserva tudo e para.
            byKey.clear();
            readable = false;
            warn.accept("Diario: rascunhos invalidos e nao consegui separa-los; arquivo preservado para recuperacao ("
                    + e.getClass().getSimpleName() + ")");
        }
    }

    /** Tira do caminho um arquivo que nao da para ler, sem apagar. */
    private void setAside(String reason) {
        Path aside = file.resolveSibling(file.getFileName() + ".ilegivel-" + System.currentTimeMillis());
        try {
            Files.move(file, aside);
            warn.accept("Diario: arquivo de rascunhos ilegivel (" + reason + "); preservado como " + aside.getFileName()
                    + " e o diario segue com um arquivo novo");
        } catch (IOException | RuntimeException e) {
            readable = false; // Nunca sobrescreve um arquivo que nao conseguimos recuperar inteiro.
            warn.accept("Diario: arquivo de rascunhos ilegivel; preservado para recuperacao (" + reason + ")");
        }
    }

    @Nullable
    private static Pending parse(JsonElement element) {
        try {
            Pending p = element.isJsonObject() ? GSON.fromJson(element, Pending.class) : null;
            if (p == null || p.draftKey() == null || p.profile() == null || p.operationId() == null
                    || p.characterId() == null || p.characterName() == null || p.document() == null
                    || p.title() == null || p.loreDate() == null) return null;
            if (!fits(p)) return null;
            return p;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Teto por registro: um rascunho enorme nao pode crescer o arquivo de todos ate o limite. */
    static boolean fits(Pending p) {
        return p.document().length() <= MAX_DOCUMENT_CHARS && p.title().length() <= MAX_FIELD_CHARS
                && p.loreDate().length() <= MAX_FIELD_CHARS && p.characterName().length() <= MAX_FIELD_CHARS
                && p.draftKey().length() <= MAX_FIELD_CHARS && p.operationId().length() <= MAX_FIELD_CHARS;
    }

    /** Agrupa mudancas enquanto o disco trabalha; serializacao e rede nunca bloqueiam o tick. */
    private CompletableFuture<Boolean> persist() {
        if (!readable) return CompletableFuture.completedFuture(false);
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        acknowledgements.add(result);
        if (!writing) {
            writing = true;
            try {
                io.execute(this::flush);
            } catch (RejectedExecutionException e) {
                writing = false;
                acknowledgements.forEach(future -> future.complete(false));
                acknowledgements.clear();
            }
        }
        return result;
    }

    private void flush() {
        while (true) {
            List<Pending> snapshot;
            List<CompletableFuture<Boolean>> waiting;
            synchronized (this) {
                snapshot = new ArrayList<>(byKey.values());
                waiting = new ArrayList<>(acknowledgements);
                acknowledgements.clear();
                flushingAcknowledgements = waiting.size();
            }
            boolean success = write(GSON.toJson(snapshot));
            synchronized (this) {
                lastWriteSucceeded = success;
                lastWriteAt = System.currentTimeMillis();
                flushingAcknowledgements = 0;
            }
            waiting.forEach(future -> future.complete(success));
            synchronized (this) {
                if (acknowledgements.isEmpty()) {
                    writing = false;
                    return;
                }
            }
        }
    }

    private boolean write(String json) {
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temp, json, StandardCharsets.UTF_8);
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException | RuntimeException e) {
            warn.accept("Diario: nao consegui gravar os rascunhos guardados (" + e.getClass().getSimpleName() + ")");
            return false;
        }
    }
}
