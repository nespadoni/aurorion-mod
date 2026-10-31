package com.aurorion.ficha.server;

import com.aurorion.ficha.sheet.DiceFormula;
import com.aurorion.ficha.sheet.Texts;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Os pedidos de teste do mestre, só em memória.
 *
 * <h2>Sem relógio</h2>
 * Nada aqui roda por tick. O prazo é conferido quando alguém toca no pedido ({@link #get},
 * {@link #claim}) e a limpeza acontece ao criar outro ({@link #purge}) — pedido vencido que ninguém
 * mais toca só ocupa memória até o próximo pedido. Quem quiser o aviso na hora (a mesa marcar
 * "venceu", o cartão sumir do HUD) agenda uma tarefa só para o prazo do pedido e chama
 * {@link #purge} nela: uma tarefa por pedido, não uma por tick. O que saiu por prazo ou por teto
 * chega a quem criou pelo {@code dropped} de {@link #create(UUID, String, Spec, Map, long, long, Consumer)}.
 * O cliente apaga o cartão sozinho pelo prazo que recebeu.
 *
 * <h2>Rolar duas vezes</h2>
 * Cada alvo tem o seu {@code command_id}, sorteado na criação. Responder de novo depois de uma falha
 * de rede manda o <b>mesmo</b> id, e o executor do site devolve o resultado já gravado em vez de
 * rolar outra vez. Enquanto a resposta não volta o alvo fica {@link State#ROLLING}: um segundo
 * aperto da tecla não sai. Resultado final ({@link State#DONE}, {@link State#DECLINED},
 * {@link State#FAILED}) não muda mais: resposta atrasada ou repetida não sobrescreve.
 *
 * <p>O id do pedido é curto (é o que o jogador sem o mod digita em {@code /ficha pedido <id>}) e só
 * precisa ser único entre os pedidos ativos. Só a thread do servidor mexe aqui; sem dependência do
 * Minecraft (testável).
 */
public final class GmRequests {
    /** O mesmo teto da mesa do mestre ({@code FichaPayloads.MAX_TABLE}): marcar todos da mesa não corta ninguém. */
    public static final int MAX_TARGETS = 48;
    public static final int MAX_PER_GM = 12;
    public static final int MAX_ACTIVE = 200;
    public static final int MAX_DC = 99;
    /** {@code kind} do pedido de dados (rolagem livre), como no {@code /rpg/roll}. */
    public static final String FREE = "free";
    private static final String ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789";

    public enum State { PENDING, ROLLING, DONE, DECLINED, FAILED }

    /**
     * O que o mestre pediu. {@code kind}: {@code skill}, {@code attribute} ou {@code free} (dados:
     * {@code formula}). {@code attribute} num teste de perícia é o atributo alternativo (vazio = o da
     * perícia). {@code dc} 0 = sem CD; nos dados, a CD compara o total ({@code total ≥ CD}).
     * {@code mode}: {@code normal}, {@code vantagem} ou {@code desvantagem}.
     *
     * <p>O construtor normaliza o que chegou torto: {@code null} vira vazio, CD fora de 1..99 vira
     * "sem CD", modo desconhecido vira {@code normal} (e não conta como {@link #privileged}), e CD
     * 0 nunca fica "oculta". Nos dados a fórmula vai à forma canônica ({@link DiceFormula}; inválida
     * vira vazia — {@link #valid}), perícia e atributo saem e o modo é sempre {@code normal}: o site
     * recusa vantagem em rolagem livre. Fora dos dados, {@code formula} é sempre vazia.
     */
    public record Spec(String kind, String skill, String attribute, int dc, boolean showDc, String mode,
                       boolean secret, String label, String formula) {
        public Spec {
            kind = kind == null ? "" : kind;
            skill = skill == null ? "" : skill;
            attribute = attribute == null ? "" : attribute;
            label = label == null ? "" : label;
            mode = "vantagem".equals(mode) || "desvantagem".equals(mode) ? mode : "normal";
            dc = dc >= 1 && dc <= MAX_DC ? dc : 0;
            showDc = showDc || dc == 0;
            if (FREE.equals(kind)) {
                String canonical = DiceFormula.normalize(formula);
                formula = canonical == null ? "" : canonical;
                skill = "";
                attribute = "";
                mode = "normal";
            } else {
                formula = "";
            }
        }

        /** Teste de atributo ou perícia. */
        public Spec(String kind, String skill, String attribute, int dc, boolean showDc, String mode,
                    boolean secret, String label) {
            this(kind, skill, attribute, dc, showDc, mode, secret, label, "");
        }

        /** Pedido de dados (rolagem livre). */
        public static Spec dice(String formula, int dc, boolean showDc, boolean secret, String label) {
            return new Spec(FREE, "", "", dc, showDc, "normal", secret, label, formula);
        }

        public boolean free() {
            return FREE.equals(kind);
        }

        /** Dados com fórmula que o motor aceita, ou teste com perícia/atributo. */
        public boolean valid() {
            return free() ? !formula.isEmpty() : !skill.isEmpty();
        }

        /** Vantagem, desvantagem e atributo alternativo só valem com mestre confirmado pelo site. */
        public boolean privileged() {
            return !"normal".equals(mode) || ("skill".equals(kind) && !attribute.isEmpty());
        }

        /** O nome do pedido: "Furtividade", "Atletismo (DES)", "Força" ou a fórmula ("2d6"). */
        public String test() {
            return free() ? formula : Texts.testName(kind, skill, attribute);
        }

        /** O que vai ao {@code /rpg/roll} como perícia, atributo ou fórmula, conforme {@code kind}. */
        public String key() {
            return free() ? formula : skill;
        }
    }

    public static final class Target {
        private final UUID id;
        private final String name;
        private final String commandId;
        private State state = State.PENDING;
        private String summary = "";

        Target(UUID id, String name, String commandId) {
            this.id = id;
            this.name = name;
            this.commandId = commandId;
        }

        public UUID id() {
            return id;
        }

        public String name() {
            return name;
        }

        public String commandId() {
            return commandId;
        }

        public State state() {
            return state;
        }

        public String summary() {
            return summary;
        }

        /** Ainda deve resposta (nem começou a rolar). */
        public boolean waiting() {
            return state == State.PENDING;
        }

        boolean open() {
            return state == State.PENDING || state == State.ROLLING;
        }
    }

    public static final class Request {
        private final String id;
        private final UUID gm;
        private final String gmName;
        private final Spec spec;
        private final long createdAt;
        private final long expiresAt;
        private final Map<UUID, Target> targets;

        Request(String id, UUID gm, String gmName, Spec spec, long createdAt, long expiresAt, Map<UUID, Target> targets) {
            this.id = id;
            this.gm = gm;
            this.gmName = gmName;
            this.spec = spec;
            this.createdAt = createdAt;
            this.expiresAt = expiresAt;
            this.targets = targets;
        }

        public String id() {
            return id;
        }

        public UUID gm() {
            return gm;
        }

        public String gmName() {
            return gmName;
        }

        public Spec spec() {
            return spec;
        }

        public long createdAt() {
            return createdAt;
        }

        public long expiresAt() {
            return expiresAt;
        }

        /** Segundos que faltam (arredondados para cima; 0 quando venceu). */
        public int secondsLeft(long now) {
            return (int) Math.max(0L, (expiresAt - now + 999L) / 1000L);
        }

        public Map<UUID, Target> targets() {
            return Collections.unmodifiableMap(targets);
        }

        @Nullable
        public Target target(UUID id) {
            return targets.get(id);
        }

        public boolean expired(long now) {
            return now >= expiresAt;
        }

        /** Todos responderam (ou recusaram): o pedido já não tem o que esperar. */
        public boolean settled() {
            for (Target t : targets.values()) if (t.open()) return false;
            return true;
        }

        /**
         * Os alvos que ainda não responderam nem começaram a rolar: de quem tirar o cartão do HUD
         * (e o que a mesa marca como "venceu"/"cancelado") quando o pedido sai.
         */
        public List<Target> waiting() {
            List<Target> out = new ArrayList<>();
            for (Target t : targets.values()) if (t.waiting()) out.add(t);
            return out;
        }
    }

    public enum Claim { OK, NOT_FOUND, NOT_TARGET, ANSWERED, BUSY }

    public record ClaimResult(Claim claim, @Nullable Request request, @Nullable Target target) {
    }

    private final Map<String, Request> requests = new LinkedHashMap<>();
    private final Random random;

    public GmRequests(Random random) {
        this.random = random;
    }

    /**
     * Cria o pedido. {@code targets}: perfil → nome exibido, na ordem da tela; o excesso além de
     * {@link #MAX_TARGETS} fica de fora. O mestre com pedidos demais perde o mais antigo.
     */
    public Request create(UUID gm, String gmName, Spec spec, Map<UUID, String> targets, long now, long ttlMs) {
        return create(gm, gmName, spec, targets, now, ttlMs, r -> { });
    }

    /**
     * Como {@link #create(UUID, String, Spec, Map, long, long)}, entregando a {@code dropped} cada
     * pedido que saiu para abrir espaço: os vencidos e o mais antigo do mestre (ou do servidor) além
     * do teto. Quem chama tira os cartões de {@link Request#waiting} e avisa a mesa.
     */
    public Request create(UUID gm, String gmName, Spec spec, Map<UUID, String> targets, long now, long ttlMs,
                          Consumer<Request> dropped) {
        purge(now).forEach(dropped);
        List<Request> mine = new ArrayList<>();
        for (Request r : requests.values()) if (r.gm.equals(gm)) mine.add(r);
        for (int i = 0; i <= mine.size() - MAX_PER_GM; i++) {
            Request old = requests.remove(mine.get(i).id);
            if (old != null) dropped.accept(old);
        }
        while (requests.size() >= MAX_ACTIVE) {
            Iterator<Request> it = requests.values().iterator();
            Request old = it.next();
            it.remove();
            dropped.accept(old);
        }

        Map<UUID, Target> chosen = new LinkedHashMap<>();
        for (Map.Entry<UUID, String> e : targets.entrySet()) {
            if (chosen.size() >= MAX_TARGETS) break;
            if (e.getKey() == null) continue;
            String name = e.getValue() == null ? "" : e.getValue();
            chosen.put(e.getKey(), new Target(e.getKey(), name, new UUID(random.nextLong(), random.nextLong()).toString()));
        }
        Request request = new Request(newId(), gm, gmName == null ? "" : gmName, spec, now, now + Math.max(1_000L, ttlMs), chosen);
        requests.put(request.id, request);
        return request;
    }

    @Nullable
    public Request get(String id, long now) {
        Request request = requests.get(id);
        if (request == null) return null;
        if (request.expired(now)) {
            requests.remove(id);
            return null;
        }
        return request;
    }

    /** O alvo começa a responder: só um aperto vira chamada ao site. */
    public ClaimResult claim(String id, UUID target, long now) {
        Request request = get(id, now);
        if (request == null) return new ClaimResult(Claim.NOT_FOUND, null, null);
        Target t = request.targets.get(target);
        if (t == null) return new ClaimResult(Claim.NOT_TARGET, request, null);
        if (t.state == State.ROLLING) return new ClaimResult(Claim.BUSY, request, t);
        if (t.state != State.PENDING) return new ClaimResult(Claim.ANSWERED, request, t);
        t.state = State.ROLLING;
        return new ClaimResult(Claim.OK, request, t);
    }

    /** Falha que vale tentar de novo (rede, site ocupado): o alvo volta a poder responder. */
    public void release(String id, UUID target) {
        Request request = requests.get(id);
        Target t = request == null ? null : request.targets.get(target);
        if (t != null && t.state == State.ROLLING) t.state = State.PENDING;
    }

    /**
     * Resultado final do alvo ({@link State#DONE} ou {@link State#FAILED}), com o resumo para o
     * mestre. Só vale para quem ainda estava aberto: um resultado final não é sobrescrito.
     */
    public void finish(String id, UUID target, State state, String summary) {
        Request request = requests.get(id);
        Target t = request == null ? null : request.targets.get(target);
        if (t == null || !t.open()) return;
        t.state = state;
        t.summary = summary == null ? "" : summary;
    }

    public ClaimResult decline(String id, UUID target, long now) {
        Request request = get(id, now);
        if (request == null) return new ClaimResult(Claim.NOT_FOUND, null, null);
        Target t = request.targets.get(target);
        if (t == null) return new ClaimResult(Claim.NOT_TARGET, request, null);
        if (t.state != State.PENDING) return new ClaimResult(t.state == State.ROLLING ? Claim.BUSY : Claim.ANSWERED, request, t);
        t.state = State.DECLINED;
        return new ClaimResult(Claim.OK, request, t);
    }

    /**
     * O mestre desiste do pedido. Só quem pediu cancela; devolve o pedido tirado (para fechar os
     * cartões de {@link Request#waiting}) ou {@code null} se ele não existe, venceu ou é de outro
     * mestre. Resposta já a caminho do site ({@link State#ROLLING}) ainda chega ao mestre: quem chama
     * guardou o pedido junto da rolagem.
     */
    @Nullable
    public Request cancel(String id, UUID gm, long now) {
        Request request = get(id, now);
        if (request == null || !request.gm.equals(gm)) return null;
        requests.remove(id);
        return request;
    }

    /** Pedidos ainda abertos para este alvo, do mais novo ao mais antigo ({@code /ficha pedido} sem id). */
    public List<Request> pendingFor(UUID target, long now) {
        purge(now);
        List<Request> out = new ArrayList<>();
        for (Request r : requests.values()) {
            Target t = r.targets.get(target);
            if (t != null && t.state == State.PENDING) out.add(r);
        }
        Collections.reverse(out);
        return out;
    }

    /** Pedidos ainda abertos deste mestre, do mais antigo ao mais novo (a mesa reaberta, o diagnóstico). */
    public List<Request> openBy(UUID gm, long now) {
        List<Request> out = new ArrayList<>();
        for (Request r : requests.values()) if (r.gm.equals(gm) && !r.expired(now)) out.add(r);
        return out;
    }

    /** Tira os vencidos e devolve quem saiu (para avisar o mestre, se ele ainda estiver olhando). */
    public List<Request> purge(long now) {
        List<Request> removed = new ArrayList<>();
        for (Iterator<Request> it = requests.values().iterator(); it.hasNext(); ) {
            Request r = it.next();
            if (r.expired(now)) {
                removed.add(r);
                it.remove();
            }
        }
        return removed;
    }

    /**
     * O que {@link #forget} mudou: {@code dropped} são os pedidos do mestre que saiu (para tirar o
     * cartão de quem ainda não respondeu); {@code abandoned}, os pedidos em que quem saiu ainda devia
     * resposta (para avisar a mesa do mestre).
     */
    public record Left(List<Request> dropped, List<Request> abandoned) {
    }

    /**
     * Quem sai do servidor (ou troca de alt, que é sair e entrar com outro perfil) deixa de
     * responder; o pedido continua para os outros alvos. Uma resposta já a caminho do site
     * ({@link State#ROLLING}) não muda: o resultado ainda chega ao mestre.
     */
    public Left forget(UUID player) {
        List<Request> dropped = new ArrayList<>();
        List<Request> abandoned = new ArrayList<>();
        for (Iterator<Request> it = requests.values().iterator(); it.hasNext(); ) {
            Request r = it.next();
            if (r.gm.equals(player)) {
                dropped.add(r);
                it.remove();
                continue;
            }
            Target t = r.targets.get(player);
            if (t != null && t.state == State.PENDING) {
                t.state = State.FAILED;
                t.summary = "saiu do servidor";
                abandoned.add(r);
            }
        }
        return new Left(List.copyOf(dropped), List.copyOf(abandoned));
    }

    public int size() {
        return requests.size();
    }

    public void clear() {
        requests.clear();
    }

    private String newId() {
        for (int length = 4; ; length++) {
            for (int attempt = 0; attempt < 20; attempt++) {
                StringBuilder id = new StringBuilder(length);
                for (int i = 0; i < length; i++) id.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
                if (!requests.containsKey(id.toString())) return id.toString();
            }
        }
    }
}
