package com.aurorion.diario.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

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
import java.util.concurrent.Executor;
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
 * <p>Sem dependência do Minecraft. A escrita em disco roda no {@link Executor} recebido (fora da
 * thread do servidor); os dados ficam sob o lock desta classe.
 */
public final class PendingStore {
    static final int MAX_TOTAL = 300;
    static final int MAX_PER_PROFILE = 25;

    /** Uma gravação esperando o site. {@code document} é o JSON do documento, já convertido. */
    public record Pending(String draftKey, String operationId, long entryId, int baseVersion,
                          String title, String loreDate, String document,
                          String profile, String characterId, String characterName,
                          long savedAt, boolean conflict) {
        Pending withConflict() {
            return new Pending(draftKey, operationId, entryId, baseVersion, title, loreDate, document,
                    profile, characterId, characterName, savedAt, true);
        }
    }

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private final Path file;
    private final Executor io;
    private final Consumer<String> warn;
    private final Map<String, Pending> byKey = new LinkedHashMap<>();

    public PendingStore(Path file, Executor io, Consumer<String> warn) {
        this.file = file;
        this.io = io;
        this.warn = warn;
        load();
    }

    /**
     * Guarda (ou substitui) a versão desta sessão. Recusa só quando o teto estoura — e aí a tela
     * avisa que o texto não está salvo em lugar nenhum, em vez de fingir que está.
     */
    public synchronized boolean put(Pending pending) {
        if (!byKey.containsKey(pending.draftKey())) {
            long sameProfile = byKey.values().stream().filter(p -> p.profile().equals(pending.profile())).count();
            if (byKey.size() >= MAX_TOTAL || sameProfile >= MAX_PER_PROFILE) return false;
        }
        byKey.put(pending.draftKey(), pending);
        persist();
        return true;
    }

    public synchronized Optional<Pending> get(String draftKey) {
        return Optional.ofNullable(byKey.get(draftKey));
    }

    public synchronized void remove(String draftKey) {
        if (byKey.remove(draftKey) != null) persist();
    }

    public synchronized void markConflict(String draftKey) {
        Pending current = byKey.get(draftKey);
        if (current != null && !current.conflict()) {
            byKey.put(draftKey, current.withConflict());
            persist();
        }
    }

    /** O que ainda vale tentar mandar (conflitos esperam a pessoa decidir). */
    public synchronized List<Pending> retryable(int limit) {
        List<Pending> out = new ArrayList<>();
        for (Pending pending : byKey.values()) {
            if (!pending.conflict()) out.add(pending);
            if (out.size() == limit) break;
        }
        return out;
    }

    /** A cópia guardada de uma entrada já existente, se houver. */
    public synchronized Optional<Pending> forEntry(String profile, long entryId) {
        if (entryId <= 0) return Optional.empty();
        return byKey.values().stream()
                .filter(p -> p.profile().equals(profile) && p.entryId() == entryId)
                .reduce((first, second) -> second.savedAt() >= first.savedAt() ? second : first);
    }

    public synchronized List<Pending> forProfile(String profile) {
        return byKey.values().stream().filter(p -> p.profile().equals(profile)).toList();
    }

    public synchronized int size() {
        return byKey.size();
    }

    // ── Disco ───────────────────────────────────────────────────────────────────

    private void load() {
        if (!Files.isRegularFile(file)) return;
        try {
            List<Pending> items = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), new TypeToken<List<Pending>>() { }.getType());
            if (items != null) {
                for (Pending pending : items) {
                    if (pending != null && pending.draftKey() != null && pending.profile() != null) byKey.put(pending.draftKey(), pending);
                }
            }
        } catch (IOException | JsonParseException e) {
            warn.accept("Diario: nao consegui ler os rascunhos guardados (" + e.getClass().getSimpleName() + ")");
        }
    }

    /** Copia o estado sob o lock; grava fora dele, no executor de E/S. */
    private void persist() {
        String json = GSON.toJson(new ArrayList<>(byKey.values()));
        io.execute(() -> write(json));
    }

    private void write(String json) {
        try {
            Files.createDirectories(file.getParent());
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temp, json, StandardCharsets.UTF_8);
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            warn.accept("Diario: nao consegui gravar os rascunhos guardados (" + e.getClass().getSimpleName() + ")");
        }
    }
}
