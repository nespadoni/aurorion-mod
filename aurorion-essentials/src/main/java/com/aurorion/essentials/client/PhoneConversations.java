package com.aurorion.essentials.client;

import com.aurorion.essentials.AurorionEssentials;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Historico do Mensagens e DMs do Gram, isolado pela mesma pasta da agenda do personagem. */
public final class PhoneConversations {
    private static final String GUI = "com.mattupolis.phone.client.gui.";
    private static final String MESSAGES = "PhoneMessagesStore";
    private static final String GRAM = "PhoneInstagramDmStore";
    private static Store messages;
    private static Store gram;
    private static boolean restoringPhoto;
    /** Fotos de DM sem arquivo local (ou ilegiveis): o Gram pede a textura a cada quadro. */
    private static final Set<String> UNRESTORABLE_PHOTOS = ConcurrentHashMap.newKeySet();
    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();
    private static final PhoneConversationWrites WRITES = new PhoneConversationWrites();

    private PhoneConversations() {}

    /** Depois de ativar a pasta e zerar a memoria antiga, antes de receber mensagens do servidor. */
    static void beginSession(Path dir) {
        UNRESTORABLE_PHOTOS.clear();
        messages = new Store(MESSAGES, dir.resolve("aurorion_messages.properties"), "PhoneThread", "PhoneMessage");
        gram = new Store(GRAM, dir.resolve("aurorion_gram_dm.properties"), "InstaThread", "InstaMessage");
        try {
            invoke(Class.forName(GUI + MESSAGES), "loadPlayerContactsIfNeeded");
        } catch (ClassNotFoundException ignored) {
            return;
        } catch (ReflectiveOperationException | RuntimeException e) {
            report(MESSAGES, e);
        }
        loadMessages();
        loadGram();
    }

    /** Nao grava: usado depois de flush na saida e antes de apagar o celular de um personagem morto. */
    static void discardSession() {
        messages = null;
        gram = null;
        UNRESTORABLE_PHOTOS.clear();
    }

    static void resetCharacter(Path dir, Set<String> keep) {
        discardSession();
        WRITES.reset(dir, () -> PhoneCharacterStorage.wipe(dir, keep));
    }

    @SuppressWarnings("unchecked")
    static void forgetGramContacts(RetiredContacts plan) throws ReflectiveOperationException {
        if (gram == null || !gram.loaded) return;
        Class<?> owner = Class.forName(GUI + GRAM);
        List<Object> threads = (List<Object>) field(owner, "THREADS");
        Map<String, ?> allMessages = (Map<String, ?>) field(owner, "MESSAGES");
        for (Object thread : new ArrayList<>(threads)) {
            if (!plan.forgets((String) call(thread, "title"))) continue;
            threads.remove(thread);
            allMessages.remove((String) call(thread, "id"));
        }
        gramChanged();
    }

    public static void loadMessages() {
        if (messages != null) messages.load();
    }

    public static void loadGram() {
        if (gram != null) gram.load();
    }

    public static void messagesChanged() {
        if (messages != null && !messages.loading) messages.dirty = true;
    }

    /**
     * A conversa aberta pede "marcar como lida" a cada tick. So conta como mudanca quando ainda ha
     * nao lidas; sem isso o historico inteiro seria copiado e comparado uma vez por segundo.
     */
    public static void threadReadRequested(String threadId) {
        if (messages == null || messages.loading || threadId == null) return;
        try {
            Object counts = field(Class.forName(GUI + MESSAGES), "UNREAD_COUNTS");
            if (counts instanceof Map<?, ?> map && map.get(threadId) instanceof Integer unread && unread > 0) {
                messages.dirty = true;
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            messages.dirty = true;
        }
    }

    public static void gramChanged() {
        if (gram != null && !gram.loading) gram.dirty = true;
    }

    /** Periodico (um segundo) e na saida, sempre antes de trocar a pasta do personagem. */
    static void flush() {
        if (messages != null) messages.save();
        if (gram != null) gram.save();
    }

    /** Fotos de DM antes so existiam em uma textura de GPU e desapareciam ao fechar o jogo. */
    public static void saveGramPhoto(String id, byte[] bytes) {
        if (restoringPhoto || gram == null || id == null || id.isBlank() || bytes == null || bytes.length == 0) return;
        try {
            UNRESTORABLE_PHOTOS.remove(id);
            Path file = gramPhotoFile(gram.file.getParent(), id);
            // Um id de rede identifica a mesma foto; nao reescreve ao receber o eco do servidor.
            if (!Files.exists(file) && WRITES.pendingPhoto(file) == null) {
                WRITES.photo(gram.file.getParent(), file, bytes, failure -> report("foto de DM", failure));
            }
        } catch (RuntimeException e) {
            report("foto de DM", e);
        }
    }

    /** Chamado apenas quando a textura falta; registrar a textura nao reenvia a foto. */
    public static Object restoreGramPhoto(String id) {
        if (restoringPhoto || gram == null || id == null || id.isBlank() || UNRESTORABLE_PHOTOS.contains(id)) return null;
        try {
            Path file = gramPhotoFile(gram.file.getParent(), id);
            byte[] bytes = WRITES.pendingPhoto(file);
            if (bytes == null) {
                if (!Files.isRegularFile(file)) {
                    UNRESTORABLE_PHOTOS.add(id);
                    return null;
                }
                bytes = Files.readAllBytes(file);
            }
            Class<?> owner = Class.forName(GUI + GRAM);
            Method register = owner.getDeclaredMethod("registerPhotoTexture", String.class, byte[].class);
            register.setAccessible(true);
            restoringPhoto = true;
            try {
                register.invoke(null, id, bytes);
            } finally {
                restoringPhoto = false;
            }
            Object texture = ((Map<?, ?>) field(owner, "DM_PHOTO_TEXTURES")).get(id);
            if (texture == null) UNRESTORABLE_PHOTOS.add(id);
            return texture;
        } catch (IOException | ReflectiveOperationException | RuntimeException e) {
            UNRESTORABLE_PHOTOS.add(id);
            report("foto de DM", e);
            return null;
        }
    }

    static Path gramPhotoFile(Path dir, String id) {
        // Nunca usa um id vindo da rede como caminho: nao permite sair da pasta do personagem.
        String name = UUID.nameUUIDFromBytes(id.getBytes(StandardCharsets.UTF_8)).toString();
        return dir.resolve("aurorion_gram_photos").resolve(name + ".image");
    }

    @SuppressWarnings("unchecked")
    private static final class Store {
        private final String name;
        private final Path file;
        private final String threadType;
        private final String messageType;
        private boolean loaded;
        private boolean loading;
        private boolean writable = true;
        private boolean dirty;
        private PhoneConversationFile.Snapshot lastSaved;

        private Store(String name, Path file, String threadType, String messageType) {
            this.name = name;
            this.file = file;
            this.threadType = threadType;
            this.messageType = messageType;
        }

        private void load() {
            if (loaded || loading) return;
            loading = true;
            try {
                Class<?> owner = Class.forName(GUI + name);
                PhoneConversationFile.Snapshot pending = WRITES.pendingSnapshot(file);
                if (pending != null || Files.exists(file)) {
                    // Reconectar antes de o disco terminar usa a versao mais recente que saiu da memoria.
                    PhoneConversationFile.Snapshot snapshot = pending != null ? pending : PhoneConversationFile.read(file);
                    Class<?> threadClass = Class.forName(GUI + name + "$" + threadType);
                    Class<?> messageClass = Class.forName(GUI + name + "$" + messageType);
                    List<Object> restoredThreads = new ArrayList<>();
                    Map<String, List<Object>> restoredMessages = new LinkedHashMap<>();
                    Map<String, Integer> restoredUnread = new HashMap<>();
                    // Prepara tudo antes de tocar na memoria: um arquivo invalido fica preservado.
                    for (var state : snapshot.threads()) {
                        Object thread = PhoneConversationFile.restore(threadClass, state.thread());
                        String id = (String) call(thread, "id");
                        if (id == null || id.isBlank() || restoredMessages.containsKey(id)) throw new IOException("Conversa invalida ou duplicada");
                        if (name.equals(MESSAGES) && !(boolean) call(thread, "playerThread")) throw new IOException("Conversa de sistema no historico de jogadores");
                        List<Object> threadMessages = new ArrayList<>();
                        for (var value : state.messages()) threadMessages.add(PhoneConversationFile.restore(messageClass, value));
                        restoredThreads.add(thread);
                        restoredMessages.put(id, threadMessages);
                        restoredUnread.put(id, state.unread());
                    }
                    List<Object> threads = (List<Object>) field(owner, "THREADS");
                    Map<String, List<Object>> allMessages = (Map<String, List<Object>>) field(owner, "MESSAGES");
                    if (name.equals(MESSAGES)) {
                        Map<String, Integer> unread = (Map<String, Integer>) field(owner, "UNREAD_COUNTS");
                        for (Object thread : new ArrayList<>(threads)) {
                            if (!(boolean) call(thread, "playerThread")) continue;
                            String id = (String) call(thread, "id");
                            threads.remove(thread);
                            allMessages.remove(id);
                            unread.remove(id);
                        }
                        unread.putAll(restoredUnread);
                    } else {
                        threads.clear();
                        allMessages.clear();
                        ((Set<String>) field(owner, "PROCESSED_NETWORK_IDS")).addAll(snapshot.processedIds());
                    }
                    threads.addAll(0, restoredThreads);
                    allMessages.putAll(restoredMessages);
                    lastSaved = snapshot;
                } else {
                    // A primeira gravacao tambem protege os contatos existentes no arquivo nativo.
                    dirty = true;
                }
            } catch (ClassNotFoundException ignored) {
                writable = false;
            } catch (IOException | ReflectiveOperationException | RuntimeException e) {
                writable = false;
                report(name + " (leitura; arquivo preservado)", e);
            } finally {
                loading = false;
                loaded = true;
            }
        }

        private void save() {
            if (!loaded || loading || !dirty || !writable) return;
            try {
                Class<?> owner = Class.forName(GUI + name);
                List<Object> threads = (List<Object>) field(owner, "THREADS");
                Map<String, List<Object>> allMessages = (Map<String, List<Object>>) field(owner, "MESSAGES");
                Map<String, Integer> unread = name.equals(MESSAGES) ? (Map<String, Integer>) field(owner, "UNREAD_COUNTS") : Map.of();
                List<PhoneConversationFile.ThreadState> states = new ArrayList<>();
                for (Object thread : threads) {
                    if (name.equals(MESSAGES) && !(boolean) call(thread, "playerThread")) continue;
                    String id = (String) call(thread, "id");
                    List<Map<String, String>> savedMessages = new ArrayList<>();
                    for (Object message : allMessages.getOrDefault(id, List.of())) savedMessages.add(PhoneConversationFile.capture(message));
                    int threadUnread = name.equals(MESSAGES) ? unread.getOrDefault(id, 0) : (int) call(thread, "unread");
                    states.add(new PhoneConversationFile.ThreadState(PhoneConversationFile.capture(thread), savedMessages, threadUnread));
                }
                Set<String> processed = name.equals(GRAM) ? (Set<String>) field(owner, "PROCESSED_NETWORK_IDS") : Set.of();
                PhoneConversationFile.Snapshot snapshot = new PhoneConversationFile.Snapshot(states, processed);
                // Abrir uma conversa ja lida nao deve regravar um arquivo a cada frame.
                if (!snapshot.equals(lastSaved)) {
                    WRITES.snapshot(file.getParent(), file, snapshot,
                            failure -> report(name + " (gravacao; tento novamente)", failure));
                }
                lastSaved = snapshot;
                dirty = false;
            } catch (ReflectiveOperationException | RuntimeException e) {
                report(name + " (gravacao; tento novamente)", e);
            }
        }
    }

    private static Object field(Class<?> owner, String name) throws ReflectiveOperationException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(null);
    }

    private static Object call(Object target, String name) throws ReflectiveOperationException {
        return target.getClass().getMethod(name).invoke(target);
    }

    private static void invoke(Class<?> owner, String name) throws ReflectiveOperationException {
        Method method = owner.getDeclaredMethod(name);
        method.setAccessible(true);
        method.invoke(null);
    }

    private static void report(String operation, Exception failure) {
        if (!REPORTED.add(operation)) return;
        AurorionEssentials.LOGGER.warn("Nao consegui persistir {} do telefone em disco.", operation, failure);
    }
}
