package com.aurorion.essentials.compat;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Os grupos de conversa do app Mensagens: quem esta em qual grupo e o que ainda falta entregar a quem
 * estava offline. E a fonte da verdade — o cliente so guarda o historico que ja recebeu.
 *
 * <p>Classe pura de proposito (so {@code java.util}): limites, dono, saida e fila sao testados sem
 * subir o jogo. Quem conhece o Minecraft e o {@link PhoneGroupData} (disco) e o
 * {@code PhoneGroupService} (rede).
 *
 * <p>Membro e a conta do perfil conectado ({@code ServerPlayer#getUUID}): o alt e outro perfil, entao
 * cada personagem tem os proprios grupos; o reset de personagem tira a conta de todos
 * ({@link #removeAccount}). O primeiro membro e sempre o dono.
 *
 * <p><b>Thread</b>: so na thread do servidor, como todo {@code SavedData}.
 */
public final class PhoneGroupBook {
    /** Com o dono. Um grupo de 16 cabe numa tela de celular sem rolar demais. */
    public static final int MAX_MEMBERS = 16;
    /** Grupos de que uma conta participa (criados ou nao): tambem limita quem pode ser adicionado. */
    public static final int MAX_GROUPS_PER_MEMBER = 10;
    /** Teto do servidor inteiro, contra um cliente adulterado criando grupos vazios em sequencia. */
    public static final int MAX_GROUPS = 2048;
    public static final int MAX_NAME = 24;
    /** O mesmo limite do campo de texto da conversa do telefone. */
    public static final int MAX_TEXT = 180;
    /** Mensagens guardadas para quem esta offline, por conta. A mais antiga sai primeiro. */
    public static final int QUEUE_PER_MEMBER = 50;
    public static final long QUEUE_TTL_MS = TimeUnit.DAYS.toMillis(7);

    public enum Result {
        OK, NOT_FOUND, NOT_MEMBER, NOT_OWNER, GROUP_FULL, TOO_MANY_GROUPS, TARGET_TOO_MANY_GROUPS,
        ALREADY_MEMBER, INVALID_NAME, INVALID_TEXT, SERVER_FULL, UNKNOWN_PLAYER, RATE_LIMITED
    }

    /** Tipos de linha de conversa. Fora {@link #TEXT}, sao avisos do proprio grupo. */
    public static final byte TEXT = 0;
    public static final byte CREATED = 1;
    public static final byte ADDED = 2;
    public static final byte REMOVED = 3;
    public static final byte LEFT = 4;

    /**
     * Uma linha guardada para quem estava offline. Nick e nome do personagem sao congelados no envio,
     * como num SMS de verdade: quem leu o grupo depois ve quem escreveu naquela hora.
     *
     * @param text a mensagem; nos avisos {@link #ADDED}/{@link #REMOVED}, o nome de quem entrou/saiu
     */
    public record Line(UUID group, byte kind, String nick, String name, String text, long at) {
    }

    public static final class Group {
        private final UUID id;
        private String name;
        /** Ordem de entrada; o primeiro e o dono. */
        private final LinkedHashSet<UUID> members = new LinkedHashSet<>();
        private final long createdAt;

        Group(UUID id, String name, long createdAt) {
            this.id = id;
            this.name = name;
            this.createdAt = createdAt;
        }

        public UUID id() {
            return id;
        }

        public String name() {
            return name;
        }

        public UUID owner() {
            return members.iterator().next();
        }

        public Set<UUID> members() {
            return Collections.unmodifiableSet(members);
        }

        public long createdAt() {
            return createdAt;
        }

        public boolean has(UUID member) {
            return members.contains(member);
        }
    }

    /** Resultado de criar: o grupo, e quem ficou de fora por ja estar em grupos demais. */
    public record Created(Result result, @Nullable Group group, List<UUID> skipped) {
    }

    private final Map<UUID, Group> groups = new LinkedHashMap<>();
    /** Indice inverso, refeito na carga: login e reset nao varrem todos os grupos. */
    private final Map<UUID, Set<UUID>> byMember = new HashMap<>();
    private final Map<UUID, ArrayDeque<Line>> queues = new HashMap<>();
    private final Runnable changed;

    public PhoneGroupBook(Runnable changed) {
        this.changed = changed;
    }

    // ---- consulta ------------------------------------------------------------------------------

    @Nullable
    public Group get(UUID id) {
        return groups.get(id);
    }

    /** Grupos da conta, na ordem em que entrou. */
    public List<Group> groupsOf(UUID member) {
        Set<UUID> ids = byMember.get(member);
        if (ids == null || ids.isEmpty()) return List.of();
        List<Group> result = new ArrayList<>(ids.size());
        for (UUID id : ids) {
            Group group = groups.get(id);
            if (group != null) result.add(group);
        }
        return result;
    }

    public int groupCount(UUID member) {
        Set<UUID> ids = byMember.get(member);
        return ids == null ? 0 : ids.size();
    }

    public Collection<Group> all() {
        return Collections.unmodifiableCollection(groups.values());
    }

    public Map<UUID, ArrayDeque<Line>> queues() {
        return Collections.unmodifiableMap(queues);
    }

    public int size() {
        return groups.size();
    }

    // ---- mudancas ------------------------------------------------------------------------------

    /**
     * Cria o grupo com o dono e os convidados. Convidado que ja esta em grupos demais fica de fora (e
     * volta em {@link Created#skipped}) em vez de impedir o grupo inteiro.
     */
    public Created create(UUID id, UUID owner, String rawName, Collection<UUID> invited, long now) {
        String name = cleanName(rawName);
        if (name == null) return new Created(Result.INVALID_NAME, null, List.of());
        if (groups.size() >= MAX_GROUPS) return new Created(Result.SERVER_FULL, null, List.of());
        if (groupCount(owner) >= MAX_GROUPS_PER_MEMBER) return new Created(Result.TOO_MANY_GROUPS, null, List.of());
        if (groups.containsKey(id)) throw new IllegalArgumentException("Id de grupo repetido");

        Group group = new Group(id, name, now);
        groups.put(id, group);
        join(group, owner);
        List<UUID> skipped = new ArrayList<>();
        for (UUID member : invited) {
            if (member.equals(owner) || group.has(member)) continue;
            if (group.members.size() >= MAX_MEMBERS || groupCount(member) >= MAX_GROUPS_PER_MEMBER) {
                skipped.add(member);
                continue;
            }
            join(group, member);
        }
        changed.run();
        return new Created(Result.OK, group, skipped);
    }

    /** So o dono adiciona. */
    public Result add(UUID groupId, UUID actor, UUID target) {
        Group group = groups.get(groupId);
        if (group == null) return Result.NOT_FOUND;
        if (!group.has(actor)) return Result.NOT_MEMBER;
        if (!group.owner().equals(actor)) return Result.NOT_OWNER;
        if (group.has(target)) return Result.ALREADY_MEMBER;
        if (group.members.size() >= MAX_MEMBERS) return Result.GROUP_FULL;
        if (groupCount(target) >= MAX_GROUPS_PER_MEMBER) return Result.TARGET_TOO_MANY_GROUPS;
        join(group, target);
        changed.run();
        return Result.OK;
    }

    /** So o dono remove, e nao a si mesmo: para isso ha o {@link #leave}. */
    public Result remove(UUID groupId, UUID actor, UUID target) {
        Group group = groups.get(groupId);
        if (group == null) return Result.NOT_FOUND;
        if (!group.has(actor)) return Result.NOT_MEMBER;
        if (!group.owner().equals(actor) || actor.equals(target)) return Result.NOT_OWNER;
        if (!group.has(target)) return Result.NOT_MEMBER;
        part(group, target);
        changed.run();
        return Result.OK;
    }

    /**
     * Sai do grupo. Se era o dono, o membro mais antigo herda; se era o ultimo, o grupo acaba.
     * Nao mexe na fila: o que ja foi guardado para quem saiu ainda e entregue (inclusive o aviso).
     */
    public Result leave(UUID groupId, UUID member) {
        Group group = groups.get(groupId);
        if (group == null) return Result.NOT_FOUND;
        if (!group.has(member)) return Result.NOT_MEMBER;
        part(group, member);
        changed.run();
        return Result.OK;
    }

    /**
     * Reset de personagem: a conta sai de todos os grupos e perde a fila. Idempotente — o reset e
     * repetido quando cai no meio.
     *
     * @return os grupos que continuam existindo e mudaram (para avisar quem ficou)
     */
    public List<Group> removeAccount(UUID account) {
        List<Group> touched = new ArrayList<>();
        for (Group group : groupsOf(account)) {
            part(group, account);
            if (groups.containsKey(group.id())) touched.add(group);
        }
        boolean hadQueue = queues.remove(account) != null;
        if (!touched.isEmpty() || hadQueue) changed.run();
        return touched;
    }

    /** Guarda para quem esta offline. Cheia, a linha mais antiga sai. */
    public void enqueue(UUID member, Line line) {
        ArrayDeque<Line> queue = queues.computeIfAbsent(member, ignored -> new ArrayDeque<>());
        while (queue.size() >= QUEUE_PER_MEMBER) queue.pollFirst();
        queue.addLast(line);
        changed.run();
    }

    /** Tudo o que esperava a conta, sem o que venceu. A fila fica vazia. */
    public List<Line> drain(UUID member, long now) {
        ArrayDeque<Line> queue = queues.remove(member);
        if (queue == null) return List.of();
        changed.run();
        List<Line> result = new ArrayList<>(queue.size());
        for (Line line : queue) {
            if (now - line.at() <= QUEUE_TTL_MS) result.add(line);
        }
        return result;
    }

    /** Descarta o que venceu de quem nunca mais entrou. Chamado na carga do arquivo. */
    public void purgeExpired(long now) {
        boolean removed = false;
        for (var it = queues.values().iterator(); it.hasNext(); ) {
            ArrayDeque<Line> queue = it.next();
            removed |= queue.removeIf(line -> now - line.at() > QUEUE_TTL_MS);
            if (queue.isEmpty()) {
                it.remove();
                removed = true;
            }
        }
        if (removed) changed.run();
    }

    // ---- carga (PhoneGroupData) ----------------------------------------------------------------

    /** Restaura um grupo do disco sem disparar {@code changed}. Membros na ordem gravada; o 1o e o dono. */
    void restore(UUID id, String name, long createdAt, List<UUID> members) {
        String clean = cleanName(name);
        if (clean == null || members.isEmpty() || groups.containsKey(id)) return;
        Group group = new Group(id, clean, createdAt);
        groups.put(id, group);
        for (UUID member : members) {
            if (group.members.size() >= MAX_MEMBERS) break;
            join(group, member);
        }
    }

    void restoreLine(UUID member, Line line) {
        ArrayDeque<Line> queue = queues.computeIfAbsent(member, ignored -> new ArrayDeque<>());
        if (queue.size() < QUEUE_PER_MEMBER) queue.addLast(line);
    }

    // ---- regras de texto -----------------------------------------------------------------------

    /** Nome sem codigo de cor nem quebra de linha, espacos juntados. {@code null} se nao sobrar nada. */
    @Nullable
    public static String cleanName(@Nullable String raw) {
        String clean = clean(raw);
        if (clean == null || clean.length() > MAX_NAME) return null;
        return clean;
    }

    /** Mensagem aceita (sem controle nem codigo de cor), ou {@code null}. */
    @Nullable
    public static String cleanText(@Nullable String raw) {
        String clean = clean(raw);
        if (clean == null || clean.length() > MAX_TEXT) return null;
        return clean;
    }

    @Nullable
    private static String clean(@Nullable String raw) {
        if (raw == null) return null;
        StringBuilder out = new StringBuilder(raw.length());
        boolean space = false;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '§') {
                // Codigo de cor do Minecraft (§ + letra): um nome de grupo nao pinta a tela dos outros.
                i++;
                continue;
            }
            if (Character.isWhitespace(c)) {
                space = true;
                continue;
            }
            if (Character.isISOControl(c) || Character.getType(c) == Character.FORMAT) continue;
            if (space && !out.isEmpty()) out.append(' ');
            space = false;
            out.append(c);
        }
        return out.isEmpty() ? null : out.toString();
    }

    // ---- internos ------------------------------------------------------------------------------

    private void join(Group group, UUID member) {
        group.members.add(member);
        byMember.computeIfAbsent(member, ignored -> new LinkedHashSet<>()).add(group.id());
    }

    private void part(Group group, UUID member) {
        group.members.remove(member);
        Set<UUID> ids = byMember.get(member);
        if (ids != null) {
            ids.remove(group.id());
            if (ids.isEmpty()) byMember.remove(member);
        }
        if (group.members.isEmpty()) groups.remove(group.id());
    }
}
