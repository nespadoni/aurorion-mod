package com.aurorion.essentials.client;

import com.aurorion.essentials.AurorionEssentials;
import com.aurorion.essentials.compat.PhoneGroupBook;
import com.aurorion.essentials.network.PhoneGroupPayloads;
import com.aurorion.essentials.network.PhoneGroupPayloads.Line;
import com.aurorion.essentials.network.PhoneGroupPayloads.Member;
import com.aurorion.essentials.network.PhoneGroupPayloads.View;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Os grupos no celular: o que o servidor disse por ultimo e a ponte com o app Mensagens do telefone.
 *
 * <p>Cada grupo vira uma conversa comum do {@code PhoneMessagesStore} (id {@value #PREFIX}&lt;uuid&gt;,
 * {@code playerThread = false}): aparece na lista, abre na mesma tela de conversa, conta nao lidas,
 * notifica e toca o som do proprio telefone. O historico e gravado pelo {@link PhoneConversations},
 * na pasta do personagem, junto com o das conversas de jogador.
 *
 * <p>Tudo do telefone por reflexao, resolvida <b>uma vez</b> ({@link Bridge}): o telefone e fechado e
 * opcional. Se algo mudar numa versao nova, os grupos se desligam com um aviso no log e o resto do
 * Mensagens segue normal. O {@code PhoneMixinContractTest} confere os nomes no build.
 */
public final class PhoneGroupsClient {
    public static final String PREFIX = "aurorion_group_";
    /** Roxo, para o grupo nao se confundir com um contato na lista. */
    static final int COLOR = 0xFF7E57C2;
    /** Um pouco acima do limite do servidor: o jitter da rede nao faz o servidor recusar. */
    private static final long SEND_INTERVAL_MS = 700L;
    private static final long ACTION_INTERVAL_MS = 1000L;
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    private static final Map<UUID, View> GROUPS = new LinkedHashMap<>();
    /** Recebeu o estado do servidor nesta conexao: o servidor tem os grupos. */
    private static boolean available;
    private static boolean pendingCreate;
    private static byte lastAction = -1;
    private static long lastSend;
    private static long lastActionAt;
    private static int counter;

    private PhoneGroupsClient() {
    }

    // ---- consulta ------------------------------------------------------------------------------

    public static boolean available() {
        return available && Bridge.get() != null;
    }

    public static boolean isGroupThread(@Nullable String threadId) {
        return threadId != null && threadId.startsWith(PREFIX);
    }

    @Nullable
    public static UUID groupOf(@Nullable String threadId) {
        if (!isGroupThread(threadId)) return null;
        try {
            return UUID.fromString(threadId.substring(PREFIX.length()));
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    public static String threadId(UUID group) {
        return PREFIX + group;
    }

    @Nullable
    public static View view(UUID group) {
        return GROUPS.get(group);
    }

    public static boolean isOwner(View view) {
        return !view.members().isEmpty() && isSelf(view.members().getFirst().nick());
    }

    public static boolean isSelf(String nick) {
        var player = Minecraft.getInstance().player;
        return player != null && nick != null && nick.equalsIgnoreCase(player.getGameProfile().getName());
    }

    /** O nome que aparece: o do personagem que o servidor mandou, ou o nick trocado pelo telefone. */
    public static String display(Member member) {
        if (!member.name().isBlank()) return member.name();
        String shown = MattupolisPhoneNames.display(member.nick());
        return shown == null || shown.isBlank() ? "?" : shown;
    }

    /** A agenda (nicks das conversas de jogador), sem o proprio, em ordem do nome mostrado. */
    public static List<String> contacts() {
        Bridge bridge = Bridge.get();
        if (bridge == null) return List.of();
        List<String> nicks = new ArrayList<>();
        try {
            for (Object thread : (List<?>) bridge.getPlayerThreads.invoke(null)) {
                String nick = (String) bridge.threadTitle.invoke(thread);
                if (nick != null && !nick.isBlank() && !isSelf(nick) && !nicks.contains(nick)) nicks.add(nick);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            Bridge.report("PhoneMessagesStore.getPlayerThreads", e);
        }
        nicks.sort(Comparator.comparing(nick -> MattupolisPhoneNames.display(nick).toLowerCase(Locale.ROOT)));
        return nicks;
    }

    public static boolean muted(String threadId) {
        Bridge bridge = Bridge.get();
        Object thread = bridge == null ? null : bridge.thread(threadId);
        try {
            return thread != null && (boolean) bridge.threadMuted.invoke(thread);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    public static void toggleMuted(String threadId) {
        Bridge bridge = Bridge.get();
        if (bridge == null || bridge.thread(threadId) == null) return;
        try {
            bridge.toggleMuted.invoke(null, threadId);
            PhoneConversations.messagesChanged();
        } catch (ReflectiveOperationException | RuntimeException e) {
            Bridge.report("PhoneMessagesStore.toggleMuted", e);
        }
    }

    // ---- pedidos ao servidor -------------------------------------------------------------------

    /** @return se o pedido saiu (falso: cedo demais desde o ultimo, ou sem servidor) */
    public static boolean create(String name, List<String> nicks) {
        if (!action(PhoneGroupPayloads.CREATE, PhoneGroupPayloads.NONE, name, nicks)) return false;
        pendingCreate = true;
        return true;
    }

    public static boolean add(UUID group, List<String> nicks) {
        return action(PhoneGroupPayloads.ADD, group, "", nicks);
    }

    public static boolean remove(UUID group, String nick) {
        return action(PhoneGroupPayloads.REMOVE, group, "", List.of(nick));
    }

    public static boolean leave(UUID group) {
        return action(PhoneGroupPayloads.LEAVE, group, "", List.of());
    }

    private static boolean action(byte action, UUID group, String name, List<String> nicks) {
        if (!available() || Minecraft.getInstance().getConnection() == null) return false;
        long now = Util.getMillis();
        if (now - lastActionAt < ACTION_INTERVAL_MS) {
            toast(Component.translatable("aurorion_essentials.phone_group.result.rate_limited").getString());
            return false;
        }
        lastActionAt = now;
        lastAction = action;
        PacketDistributor.sendToServer(new PhoneGroupPayloads.Action(action, group, name,
                List.copyOf(nicks.subList(0, Math.min(nicks.size(), PhoneGroupBook.MAX_MEMBERS)))));
        return true;
    }

    // ---- ganchos do app Mensagens (mixins) -----------------------------------------------------

    private static Field chatThreadField;
    private static Field chatInputField;
    private static boolean chatFieldsResolved;

    /** O {@code threadId} da tela de conversa. Campos resolvidos uma vez; chamado so em clique e tecla. */
    @Nullable
    public static String chatThreadId(Object chat) {
        resolveChatFields(chat);
        try {
            return chatThreadField != null && chatThreadField.get(chat) instanceof String id ? id : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    @Nullable
    public static EditBox chatInput(Object chat) {
        resolveChatFields(chat);
        try {
            return chatInputField != null && chatInputField.get(chat) instanceof EditBox box ? box : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static void resolveChatFields(Object chat) {
        if (chatFieldsResolved) return;
        chatFieldsResolved = true;
        try {
            chatThreadField = chat.getClass().getDeclaredField("threadId");
            chatThreadField.setAccessible(true);
            chatInputField = chat.getClass().getDeclaredField("messageInput");
            chatInputField.setAccessible(true);
        } catch (ReflectiveOperationException | RuntimeException e) {
            Bridge.report("PhoneMessageChatScreen.threadId/messageInput", e);
        }
    }

    /**
     * Enviar numa conversa de grupo. Substitui o envio do telefone, que so manda para jogador e, para
     * qualquer outra conversa, responderia sozinho "Mensagem recebida".
     *
     * @return se a conversa era de grupo (o original nao roda)
     */
    public static boolean handleChatSend(String threadId, @Nullable EditBox input) {
        UUID group = groupOf(threadId);
        if (group == null) return false;
        if (input == null) return true;
        if (PhoneUi.airplaneMode()) {
            PhoneUi.playError();
            PhoneUi.toast(PhoneUi.text("airplane_no_message", "Modo avião ativo."));
            return true;
        }
        String text = PhoneGroupBook.cleanText(input.getValue());
        if (text == null) return true;
        if (!GROUPS.containsKey(group) || !available()) {
            PhoneUi.playError();
            PhoneUi.toast(Component.translatable("aurorion_essentials.phone_group.result.not_member").getString());
            return true;
        }
        long now = Util.getMillis();
        if (now - lastSend < SEND_INTERVAL_MS) {
            // O texto fica no campo: nada se perde, so espera.
            PhoneUi.toast(Component.translatable("aurorion_essentials.phone_group.result.rate_limited").getString());
            return true;
        }
        Bridge bridge = Bridge.get();
        if (bridge == null) return true;
        lastSend = now;
        try {
            bridge.addPlayerMessage.invoke(null, threadId, text);
        } catch (ReflectiveOperationException | RuntimeException e) {
            Bridge.report("PhoneMessagesStore.addPlayerMessage", e);
        }
        PacketDistributor.sendToServer(new PhoneGroupPayloads.Send(group, text));
        input.setValue("");
        bridge.sound("playMessageSend");
        return true;
    }

    /** Foto, camera e localizacao: grupo e so texto. @return se bloqueou */
    public static boolean blockAttachment(@Nullable String threadId) {
        if (!isGroupThread(threadId)) return false;
        PhoneUi.playError();
        PhoneUi.toast(Component.translatable("aurorion_essentials.phone_group.text_only").getString());
        return true;
    }

    /** Toque no cabecalho da conversa de grupo: membros, adicionar, sair. */
    public static boolean openInfo(@Nullable String threadId) {
        UUID group = groupOf(threadId);
        if (group == null) return false;
        PhoneUi.playClick();
        Minecraft.getInstance().setScreen(new PhoneGroupInfoScreen(group));
        return true;
    }

    /** "+ Adicionar" da lista de conversas: nova conversa ou novo grupo. @return se abriu o menu */
    public static boolean openNewMenu(Screen messages) {
        if (!available()) return false;
        PhoneUi.playClick();
        Minecraft.getInstance().setScreen(new PhoneChoiceSheet(messages,
                Component.translatable("aurorion_essentials.phone_group.new_title").getString(), List.of(
                new PhoneChoiceSheet.Option(Component.translatable("aurorion_essentials.phone_group.new_chat").getString(),
                        () -> PhoneUi.open(PhoneUi.newScreen("PhoneNewMessageScreen", new Class<?>[0]))),
                new PhoneChoiceSheet.Option(Component.translatable("aurorion_essentials.phone_group.new_group").getString(),
                        () -> Minecraft.getInstance().setScreen(PhoneGroupEditScreen.create())))));
        return true;
    }

    public static void openChat(UUID group) {
        PhoneUi.open(PhoneUi.newScreen("PhoneMessageChatScreen", new Class<?>[]{String.class}, threadId(group)));
    }

    public static void openMessages() {
        PhoneUi.open(PhoneUi.newScreen("PhoneMessagesScreen", new Class<?>[0]));
    }

    /** Apaga a conversa de um grupo de que o jogador ja nao participa. */
    public static void deleteConversation(UUID group) {
        if (GROUPS.containsKey(group)) return;
        Bridge bridge = Bridge.get();
        if (bridge == null) return;
        String id = threadId(group);
        bridge.threads.removeIf(thread -> id.equals(bridge.id(thread)));
        bridge.messages.remove(id);
        bridge.unread.remove(id);
        PhoneConversations.messagesChanged();
    }

    // ---- servidor -> cliente -------------------------------------------------------------------

    public static void applySync(PhoneGroupPayloads.Sync sync) {
        available = true;
        Bridge bridge = Bridge.get();
        if (bridge == null) return;
        bridge.loadHistory();

        if (sync.full()) {
            GROUPS.clear();
            // O que esta no celular e nao veio na lista: o personagem nao participa mais.
            for (Object thread : List.copyOf(bridge.threads)) {
                UUID group = groupOf(bridge.id(thread));
                if (group != null && sync.groups().stream().noneMatch(view -> view.id().equals(group))) {
                    bridge.upsert(group, null, null);
                }
            }
        }
        UUID created = null;
        for (View view : sync.groups()) {
            if (!GROUPS.containsKey(view.id()) && bridge.thread(threadId(view.id())) == null) created = view.id();
            GROUPS.put(view.id(), view);
            bridge.upsert(view.id(), view.name(), membersLabel(view));
        }
        for (UUID gone : sync.gone()) {
            boolean wasMember = GROUPS.remove(gone) != null;
            if (bridge.thread(threadId(gone)) == null) continue;
            bridge.upsert(gone, null, null);
            if (wasMember) bridge.line(gone, Component.translatable("aurorion_essentials.phone_group.line.you_left").getString(),
                    System.currentTimeMillis(), false);
        }
        PhoneConversations.messagesChanged();

        if (pendingCreate && created != null) {
            pendingCreate = false;
            if (Minecraft.getInstance().screen instanceof PhoneGroupEditScreen) openChat(created);
        }
    }

    public static void receive(PhoneGroupPayloads.Messages payload) {
        Bridge bridge = Bridge.get();
        if (bridge == null) return;
        bridge.loadHistory();
        for (Line line : payload.lines()) {
            View view = GROUPS.get(line.group());
            if (bridge.thread(threadId(line.group())) == null) {
                // Aviso de um grupo que o celular nem conhece (outro PC, historico apagado): nao cria conversa.
                if (view == null && line.kind() != PhoneGroupBook.TEXT) continue;
                bridge.upsert(line.group(), view != null ? view.name()
                        : Component.translatable("aurorion_essentials.phone_group.unknown").getString(),
                        view != null ? membersLabel(view) : "");
            }
            boolean mine = isSelf(line.nick());
            String text = render(line);
            bridge.line(line.group(), text, line.at(), !mine);
            if (!mine) {
                String title = view != null ? view.name() : Component.translatable("aurorion_essentials.phone_group.unknown").getString();
                bridge.notify(threadId(line.group()), title, text);
            }
        }
        PhoneConversations.messagesChanged();
    }

    public static void notice(PhoneGroupPayloads.Notice notice) {
        PhoneGroupBook.Result result = notice.value();
        // O servidor manda o estado antes da resposta: se o grupo nasceu, a conversa ja abriu.
        if (lastAction == PhoneGroupPayloads.CREATE) pendingCreate = false;
        String key = switch (result) {
            case OK -> switch (lastAction) {
                case PhoneGroupPayloads.CREATE -> "aurorion_essentials.phone_group.done.create";
                case PhoneGroupPayloads.ADD -> "aurorion_essentials.phone_group.done.add";
                case PhoneGroupPayloads.REMOVE -> "aurorion_essentials.phone_group.done.remove";
                case PhoneGroupPayloads.LEAVE -> "aurorion_essentials.phone_group.done.leave";
                default -> null;
            };
            default -> "aurorion_essentials.phone_group.result." + result.name().toLowerCase(Locale.ROOT);
        };
        if (key == null) return;
        if (result != PhoneGroupBook.Result.OK) PhoneUi.playError();
        toast(Component.translatable(key).getString());
    }

    /** Entrada e saida de um servidor: o estado e da conexao. */
    public static void reset() {
        GROUPS.clear();
        available = false;
        pendingCreate = false;
        lastAction = -1;
        lastSend = 0L;
        lastActionAt = 0L;
    }

    // ---- texto ---------------------------------------------------------------------------------

    static String membersLabel(View view) {
        return Component.translatable("aurorion_essentials.phone_group.members", view.members().size()).getString();
    }

    private static String render(Line line) {
        String who = isSelf(line.nick())
                ? Component.translatable("aurorion_essentials.phone_group.you").getString()
                : display(new Member(line.nick(), line.name()));
        return switch (line.kind()) {
            case PhoneGroupBook.TEXT -> who + ": " + line.text();
            case PhoneGroupBook.CREATED -> Component.translatable("aurorion_essentials.phone_group.line.created", who).getString();
            case PhoneGroupBook.ADDED -> Component.translatable("aurorion_essentials.phone_group.line.added", who, line.text()).getString();
            case PhoneGroupBook.REMOVED -> Component.translatable("aurorion_essentials.phone_group.line.removed", who, line.text()).getString();
            case PhoneGroupBook.LEFT -> Component.translatable("aurorion_essentials.phone_group.line.left", who).getString();
            default -> line.text();
        };
    }

    static void toast(String message) {
        if (Minecraft.getInstance().screen instanceof PhoneGroupScreen screen) screen.toast(message);
        else PhoneUi.toast(message);
    }

    private static String clock(long at) {
        ZonedDateTime time = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault());
        return time.toLocalDate().equals(LocalDate.now()) ? time.format(CLOCK) : time.format(DAY);
    }

    // ---- ponte com o PhoneMessagesStore --------------------------------------------------------

    /**
     * Os membros do {@code PhoneMessagesStore} que os grupos usam, resolvidos uma vez. As colecoes
     * sao {@code static final} no telefone: guardar a referencia vale pela vida do jogo.
     */
    @SuppressWarnings("unchecked")
    private static final class Bridge {
        private static final String GUI = "com.mattupolis.phone.client.gui.";
        private static Bridge instance;
        private static boolean resolved;
        private static final Set<String> REPORTED = java.util.concurrent.ConcurrentHashMap.newKeySet();

        final List<Object> threads;
        final Map<String, List<Object>> messages;
        final Map<String, Integer> unread;
        final Method threadId;
        final Method threadTitle;
        final Method threadSubtitle;
        final Method threadColor;
        final Method threadPinned;
        final Method threadArchived;
        final Method threadMuted;
        final Constructor<?> newThread;
        final Constructor<?> newMessage;
        final Method addPlayerMessage;
        final Method getPlayerThreads;
        final Method toggleMuted;
        final Method loadContacts;
        final Method playIncomingSound;
        final Method addNotification;
        final Class<?> sounds;

        private Bridge() throws ReflectiveOperationException {
            Class<?> store = Class.forName(GUI + "PhoneMessagesStore");
            Class<?> thread = Class.forName(GUI + "PhoneMessagesStore$PhoneThread");
            Class<?> message = Class.forName(GUI + "PhoneMessagesStore$PhoneMessage");
            threads = (List<Object>) staticField(store, "THREADS");
            messages = (Map<String, List<Object>>) staticField(store, "MESSAGES");
            unread = (Map<String, Integer>) staticField(store, "UNREAD_COUNTS");
            threadId = thread.getMethod("id");
            threadTitle = thread.getMethod("title");
            threadSubtitle = thread.getMethod("subtitle");
            threadColor = thread.getMethod("color");
            threadPinned = thread.getMethod("pinned");
            threadArchived = thread.getMethod("archived");
            threadMuted = thread.getMethod("muted");
            newThread = thread.getConstructor(String.class, String.class, String.class, int.class,
                    boolean.class, boolean.class, boolean.class, boolean.class);
            newMessage = message.getConstructor(String.class, String.class, boolean.class, String.class, String.class,
                    String.class, Path.class, int.class, int.class, int.class);
            addPlayerMessage = store.getMethod("addPlayerMessage", String.class, String.class);
            getPlayerThreads = store.getMethod("getPlayerThreads");
            toggleMuted = store.getMethod("toggleMuted", String.class);
            loadContacts = declared(store, "loadPlayerContactsIfNeeded");
            playIncomingSound = declared(store, "playIncomingSoundForThread", String.class);
            addNotification = Class.forName(GUI + "PhoneNotificationStore")
                    .getMethod("addPlayerNotification", String.class, String.class, String.class);
            sounds = Class.forName(GUI + "PhoneSoundManager");
        }

        @Nullable
        static Bridge get() {
            if (resolved) return instance;
            resolved = true;
            try {
                instance = new Bridge();
            } catch (ClassNotFoundException ignored) {
                // Sem telefone: sem grupos.
            } catch (ReflectiveOperationException | RuntimeException e) {
                report("PhoneMessagesStore (grupos desligados)", e);
            }
            return instance;
        }

        void loadHistory() {
            try {
                loadContacts.invoke(null);
            } catch (ReflectiveOperationException | RuntimeException e) {
                report("PhoneMessagesStore.loadPlayerContactsIfNeeded", e);
            }
        }

        @Nullable
        String id(Object thread) {
            try {
                return (String) threadId.invoke(thread);
            } catch (ReflectiveOperationException | RuntimeException e) {
                return null;
            }
        }

        @Nullable
        Object thread(String id) {
            for (Object thread : threads) {
                if (id.equals(id(thread))) return thread;
            }
            return null;
        }

        /**
         * Cria ou atualiza a conversa. {@code name == null} mantem o nome e marca que o jogador saiu;
         * fixado, arquivado e silenciado sao do jogador e ficam.
         */
        void upsert(UUID group, @Nullable String name, @Nullable String subtitle) {
            String id = threadId(group);
            String gone = Component.translatable("aurorion_essentials.phone_group.gone").getString();
            try {
                for (int i = 0; i < threads.size(); i++) {
                    Object old = threads.get(i);
                    if (!id.equals(id(old))) continue;
                    String title = name != null ? name : (String) threadTitle.invoke(old);
                    String sub = name != null ? (subtitle == null ? "" : subtitle) : gone;
                    if (title.equals(threadTitle.invoke(old)) && sub.equals(threadSubtitle.invoke(old))) return;
                    threads.set(i, newThread.newInstance(id, title, sub, (int) threadColor.invoke(old), false,
                            (boolean) threadPinned.invoke(old), (boolean) threadArchived.invoke(old), (boolean) threadMuted.invoke(old)));
                    return;
                }
                if (name == null) return;
                threads.addFirst(newThread.newInstance(id, name, subtitle == null ? "" : subtitle, COLOR, false, false, false, false));
                messages.computeIfAbsent(id, ignored -> new ArrayList<>());
                unread.putIfAbsent(id, 0);
            } catch (ReflectiveOperationException | RuntimeException e) {
                report("PhoneMessagesStore$PhoneThread", e);
            }
        }

        /**
         * Uma linha recebida, com a hora de envio (a fila de quem estava offline chega toda no login).
         * O id comeca com {@code msg_<millis>_}, que e o que a lista do telefone usa para ordenar.
         */
        void line(UUID group, String text, long at, boolean countUnread) {
            String id = threadId(group);
            try {
                Object message = newMessage.newInstance("msg_" + at + "_g" + (counter++), text, false, clock(at), "",
                        "text", null, 0, 0, 0);
                messages.computeIfAbsent(id, ignored -> new ArrayList<>()).add(message);
                for (int i = 0; i < threads.size(); i++) {
                    if (!id.equals(id(threads.get(i)))) continue;
                    Object thread = threads.remove(i);
                    threads.addFirst(thread);
                    if (countUnread && !(boolean) threadMuted.invoke(thread)) unread.merge(id, 1, Integer::sum);
                    break;
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                report("PhoneMessagesStore$PhoneMessage", e);
            }
        }

        /** Notificacao e som pelo caminho do proprio telefone (respeita silenciado e conversa aberta). */
        void notify(String threadId, String title, String text) {
            Object thread = thread(threadId);
            try {
                if (thread != null && (boolean) threadMuted.invoke(thread)) return;
                addNotification.invoke(null, PhoneUi.text("messages", "Mensagens"), title, text);
                playIncomingSound.invoke(null, threadId);
            } catch (ReflectiveOperationException | RuntimeException e) {
                report("PhoneNotificationStore.addPlayerNotification", e);
            }
        }

        void sound(String name) {
            try {
                sounds.getMethod(name).invoke(null);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Sem som, mesma mensagem.
            }
        }

        private static Object staticField(Class<?> owner, String name) throws ReflectiveOperationException {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(null);
        }

        private static Method declared(Class<?> owner, String name, Class<?>... types) throws ReflectiveOperationException {
            Method method = owner.getDeclaredMethod(name, types);
            method.setAccessible(true);
            return method;
        }

        static void report(String what, Exception e) {
            if (!REPORTED.add(what)) return;
            AurorionEssentials.LOGGER.warn("Grupos do telefone: nao consegui usar {}.", what, e);
        }
    }
}
