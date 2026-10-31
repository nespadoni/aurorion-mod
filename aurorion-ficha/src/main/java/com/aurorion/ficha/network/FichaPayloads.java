package com.aurorion.ficha.network;

import com.aurorion.ficha.AurorionFicha;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Os pacotes da ficha.
 *
 * <p>O que vem do site (ficha, candidatos, rolagem) viaja como JSON já conferido e recortado pelos
 * modelos de {@code sheet/} — o cliente lê com o mesmo parser. Todo texto tem teto no próprio codec:
 * um cliente adulterado não aloca além disso no servidor, e o servidor ainda revalida tudo. Os
 * tetos para o cliente cabem com folga no limite de 1 MiB do pacote servidor → cliente; os do
 * cliente, no de 32 KiB do caminho contrário.
 *
 * <h2>Escrever nunca derruba</h2>
 * {@code writeUtf(texto, max)} lança exceção quando o texto passa do teto, e exceção no encoder
 * desconecta o jogador. Por isso todo texto sai por {@link #utf}, que corta no teto (sem partir um
 * par de surrogates), e cada record troca {@code null} por vazio no construtor. Quem lê continua
 * estrito: {@code readUtf(max)} recusa o que passou do teto, e lista com contagem fora do teto é
 * pacote adulterado ({@link #count}) — cortar e seguir deixaria o resto do buffer desalinhado.
 *
 * <p>Mudar o formato de um pacote que já existe exige subir a versão do canal
 * ({@code FichaNetwork.PROTOCOL_VERSION}); acrescentar um pacote novo, não.
 */
public final class FichaPayloads {
    /** Ficha + vínculo + candidatos de {@code needs_binding}: bem abaixo do teto. */
    public static final int VIEW_JSON = 196_608;
    public static final int CANDIDATES_JSON = 196_608;
    public static final int CARD_JSON = 8_192;
    public static final int MESSAGE = 300;
    public static final int NAME = 64;
    public static final int KEY = 64;
    public static final int LABEL = 80;
    public static final int DETAIL = 160;
    public static final int CODE = 24;
    public static final int URL = 300;
    public static final int WORD = 16;
    public static final int PROFILE = 16;
    public static final int REQUEST_ID = 12;
    /** Fórmula de dados do pedido do mestre ({@code DiceFormula.MAX_LENGTH}; o site aceita até 120). */
    public static final int FORMULA = 60;
    public static final int MAX_TABLE = 48;

    /** Bits de {@link View#flags()}. */
    public static final int OPEN = 1;
    /** Este jogador pode abrir a mesa do mestre (confere de novo no servidor ao abrir). */
    public static final int GM = 2;
    /** Abrir direto na aba Vínculo (veio de /ficha vincular ou do aviso de alt). */
    public static final int TAB_BIND = 4;
    /** Ficha de outro jogador vista pelo mestre: só leitura, sem rolar nem vincular. */
    public static final int PEEK = 8;
    /** O perfil é um alt (segundo personagem): a aba Vínculo explica a escolha. */
    public static final int ALT = 16;
    /**
     * Abrir sozinha se o jogador não estiver com outra tela (o aviso de alt sem ficha). Vai sem
     * {@link #OPEN}: cliente que não conhece o bit só atualiza uma ficha já aberta.
     */
    public static final int PROMPT = 32;

    private FichaPayloads() {
    }

    private static ResourceLocation channel(String path) {
        return ResourceLocation.fromNamespaceAndPath(AurorionFicha.MOD_ID, path);
    }

    /** Corta em {@code max} caracteres sem partir um par de surrogates; {@code null} vira vazio. */
    public static String clip(@Nullable String text, int max) {
        if (text == null) return "";
        if (text.length() <= max) return text;
        int end = Math.max(0, max);
        if (end > 0 && Character.isHighSurrogate(text.charAt(end - 1))) end--;
        return text.substring(0, end);
    }

    /** {@code writeUtf} que corta em vez de lançar (ver "Escrever nunca derruba"). */
    static void utf(FriendlyByteBuf buf, @Nullable String text, int max) {
        buf.writeUtf(clip(text, max), max);
    }

    /** Contagem de lista lida do buffer: fora de {@code 0..max} é pacote adulterado. */
    static int count(FriendlyByteBuf buf, int max, String what) {
        int count = buf.readVarInt();
        if (count < 0 || count > max) throw new DecoderException("ficha: " + what + " demais (" + count + ")");
        return count;
    }

    private static String s(@Nullable String text) {
        return text == null ? "" : text;
    }

    /** UUID nulo é bug de quem montou o pacote; o zero não aponta para ninguém. */
    private static UUID u(@Nullable UUID id) {
        return id == null ? new UUID(0L, 0L) : id;
    }

    // ── Servidor → cliente ──────────────────────────────────────────────────────

    /**
     * Abre (com {@link #OPEN}) ou atualiza a tela. {@code code}: o do site ({@code ok},
     * {@code needs_binding}, {@code no_sheet}, {@code not_linked}, {@code foreign}...). {@code json}:
     * {@code {sheet, binding, candidates, profile_name, peek_name}}.
     */
    public record View(int flags, String code, String json, String siteUrl, String message) implements CustomPacketPayload {
        public static final Type<View> TYPE = new Type<>(channel("view"));
        public static final StreamCodec<RegistryFriendlyByteBuf, View> STREAM_CODEC = StreamCodec.of(
                (buf, data) -> {
                    buf.writeVarInt(data.flags);
                    utf(buf, data.code, CODE);
                    utf(buf, data.json, VIEW_JSON);
                    utf(buf, data.siteUrl, URL);
                    utf(buf, data.message, MESSAGE);
                },
                buf -> new View(buf.readVarInt(), buf.readUtf(CODE), buf.readUtf(VIEW_JSON), buf.readUtf(URL), buf.readUtf(MESSAGE)));

        public View {
            code = s(code);
            json = s(json);
            siteUrl = s(siteUrl);
            message = s(message);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Resposta de {@code /rpg/candidates}: {@code {code, candidates, binding}}. */
    public record Candidates(String json) implements CustomPacketPayload {
        public static final Type<Candidates> TYPE = new Type<>(channel("candidates"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Candidates> STREAM_CODEC = StreamCodec.of(
                (buf, data) -> utf(buf, data.json, CANDIDATES_JSON),
                buf -> new Candidates(buf.readUtf(CANDIDATES_JSON)));

        public Candidates {
            json = s(json);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Um cartão de rolagem ({@code RollCard} em JSON), já redigido para quem recebe. */
    public record Card(String json) implements CustomPacketPayload {
        public static final Type<Card> TYPE = new Type<>(channel("card"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Card> STREAM_CODEC = StreamCodec.of(
                (buf, data) -> utf(buf, data.json, CARD_JSON),
                buf -> new Card(buf.readUtf(CARD_JSON)));

        public Card {
            json = s(json);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Pedido do mestre para o HUD. {@code active=false} tira o cartão (respondido, recusado, vencido,
     * cancelado); nele, {@code detail} não vazio é o motivo para o jogador ("foi cancelado pelo mestre")
     * e {@code test} o nome do teste. A CD escondida nunca chega aqui: {@code detail} já vem montado
     * pelo servidor.
     */
    public record Request(String id, String gmName, String title, String test, String detail, int seconds,
                          boolean active) implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(channel("request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Request> STREAM_CODEC = StreamCodec.of(
                (buf, data) -> {
                    utf(buf, data.id, REQUEST_ID);
                    utf(buf, data.gmName, NAME);
                    utf(buf, data.title, LABEL);
                    utf(buf, data.test, LABEL);
                    utf(buf, data.detail, DETAIL);
                    buf.writeVarInt(Math.max(0, data.seconds));
                    buf.writeBoolean(data.active);
                },
                buf -> new Request(buf.readUtf(REQUEST_ID), buf.readUtf(NAME), buf.readUtf(LABEL), buf.readUtf(LABEL),
                        buf.readUtf(DETAIL), buf.readVarInt(), buf.readBoolean()));

        public Request {
            id = s(id);
            gmName = s(gmName);
            title = s(title);
            test = s(test);
            detail = s(detail);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Resultado de uma ação da tela que não é uma ficha nova: {@code state} {@code ok},
     * {@code erro}, {@code ocupado} (dá para repetir já); {@code context} {@code abrir}, {@code rolar},
     * {@code vincular} (também a lista de candidatos), {@code mestre} (mesa, pedido, cancelar e a
     * ficha espiada).
     */
    public record Status(String context, String state, String message) implements CustomPacketPayload {
        public static final Type<Status> TYPE = new Type<>(channel("status"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Status> STREAM_CODEC = StreamCodec.of(
                (buf, data) -> {
                    utf(buf, data.context, WORD);
                    utf(buf, data.state, WORD);
                    utf(buf, data.message, MESSAGE);
                },
                buf -> new Status(buf.readUtf(WORD), buf.readUtf(WORD), buf.readUtf(MESSAGE)));

        public Status {
            context = s(context);
            state = s(state);
            message = s(message);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Um jogador na mesa do mestre. {@code distance} em blocos; {@code modded}: tem a tela/HUD;
     * {@code sheet}: o que o servidor já sabe da ficha sem perguntar ao site — {@code ok},
     * {@code sem_ficha}, {@code sem_vinculo}, {@code sem_construcao} (ficha antiga, sem a construção nova
     * aprovada) ou vazio (ainda não conferida).
     */
    public record Player(UUID id, String profile, String name, int distance, boolean modded, String sheet) {
        public Player {
            id = u(id);
            profile = s(profile);
            name = s(name);
            sheet = s(sheet);
        }

        public Player(UUID id, String profile, String name, int distance, boolean modded) {
            this(id, profile, name, distance, modded, "");
        }
    }

    /** Abre a mesa do mestre com os jogadores por perto (no máximo {@link #MAX_TABLE}). */
    public record Table(List<Player> players, int radius, int requestSeconds) implements CustomPacketPayload {
        public static final Type<Table> TYPE = new Type<>(channel("table"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Table> STREAM_CODEC = StreamCodec.of(
                (buf, data) -> {
                    int count = Math.min(data.players.size(), MAX_TABLE);
                    buf.writeVarInt(count);
                    for (int i = 0; i < count; i++) {
                        Player p = data.players.get(i);
                        buf.writeUUID(p.id());
                        utf(buf, p.profile(), PROFILE);
                        utf(buf, p.name(), NAME);
                        buf.writeVarInt(Math.max(0, p.distance()));
                        buf.writeBoolean(p.modded());
                        utf(buf, p.sheet(), WORD);
                    }
                    buf.writeVarInt(Math.max(0, data.radius));
                    buf.writeVarInt(Math.max(0, data.requestSeconds));
                },
                buf -> {
                    int count = count(buf, MAX_TABLE, "jogadores na mesa");
                    List<Player> players = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        players.add(new Player(buf.readUUID(), buf.readUtf(PROFILE), buf.readUtf(NAME), buf.readVarInt(), buf.readBoolean(),
                                buf.readUtf(WORD)));
                    }
                    return new Table(players, buf.readVarInt(), buf.readVarInt());
                });

        public Table {
            players = players == null ? List.of() : players;
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Resposta de um alvo chegando à mesa: {@code state} {@code aguardando}, {@code rolando},
     * {@code rolou}, {@code recusou}, {@code erro}, {@code expirou}, {@code cancelado}. {@code card}:
     * o cartão ({@code RollCard} em JSON) quando rolou; vazio nos outros casos.
     */
    public record TableUpdate(String requestId, String test, UUID target, String name, String state, String detail,
                              String card) implements CustomPacketPayload {
        public static final Type<TableUpdate> TYPE = new Type<>(channel("table_update"));
        public static final StreamCodec<RegistryFriendlyByteBuf, TableUpdate> STREAM_CODEC = StreamCodec.of(
                (buf, data) -> {
                    utf(buf, data.requestId, REQUEST_ID);
                    utf(buf, data.test, LABEL);
                    buf.writeUUID(data.target);
                    utf(buf, data.name, NAME);
                    utf(buf, data.state, WORD);
                    utf(buf, data.detail, MESSAGE);
                    utf(buf, data.card, CARD_JSON);
                },
                buf -> new TableUpdate(buf.readUtf(REQUEST_ID), buf.readUtf(LABEL), buf.readUUID(), buf.readUtf(NAME),
                        buf.readUtf(WORD), buf.readUtf(MESSAGE), buf.readUtf(CARD_JSON)));

        public TableUpdate {
            requestId = s(requestId);
            test = s(test);
            target = u(target);
            name = s(name);
            state = s(state);
            detail = s(detail);
            card = s(card);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Aviso curto no HUD (ex.: alt sem ficha), por {@code seconds} segundos. */
    public record Notice(String title, String text, int seconds) implements CustomPacketPayload {
        public static final Type<Notice> TYPE = new Type<>(channel("notice"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Notice> STREAM_CODEC = StreamCodec.of(
                (buf, data) -> {
                    utf(buf, data.title, LABEL);
                    utf(buf, data.text, MESSAGE);
                    buf.writeVarInt(Math.max(0, data.seconds));
                },
                buf -> new Notice(buf.readUtf(LABEL), buf.readUtf(MESSAGE), buf.readVarInt()));

        public Notice {
            title = s(title);
            text = s(text);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // ── Cliente → servidor ──────────────────────────────────────────────────────

    /**
     * Ação sem rolagem. {@code action}: {@code abrir}, {@code atualizar} ({@code a}=a versão que a
     * tela mostra; o servidor só responde, com uma View sem OPEN, se ela mudou), {@code candidatos}, {@code vincular}
     * ({@code a}=subject_type, {@code b}=subject_id), {@code limpar}, {@code mestre}, {@code espiar}
     * ({@code a}=perfil), {@code recusar} ({@code a}=id do pedido), {@code cancelar} ({@code a}=id do
     * pedido; só o mestre que pediu).
     */
    public record Action(String action, String a, String b) implements CustomPacketPayload {
        public static final Type<Action> TYPE = new Type<>(channel("action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Action> STREAM_CODEC = StreamCodec.of(
                (buf, data) -> {
                    utf(buf, data.action, WORD);
                    utf(buf, data.a, KEY);
                    utf(buf, data.b, KEY);
                },
                buf -> new Action(buf.readUtf(WORD), buf.readUtf(KEY), buf.readUtf(KEY)));

        public Action {
            action = s(action);
            a = s(a);
            b = s(b);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Rolar. {@code kind}: {@code skill} ({@code key}=perícia), {@code attribute} ({@code key}=atributo),
     * {@code free} ({@code key}=fórmula) ou {@code pedido} ({@code requestId}; o resto vem do pedido).
     * {@code dc} 0 = sem CD. O {@code command_id} é sorteado no servidor, nunca aqui.
     */
    public record Roll(String kind, String key, int dc, boolean secret, String requestId) implements CustomPacketPayload {
        public static final Type<Roll> TYPE = new Type<>(channel("roll"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Roll> STREAM_CODEC = StreamCodec.of(
                (buf, data) -> {
                    utf(buf, data.kind, 12);
                    utf(buf, data.key, KEY);
                    buf.writeVarInt(data.dc);
                    buf.writeBoolean(data.secret);
                    utf(buf, data.requestId, REQUEST_ID);
                },
                buf -> new Roll(buf.readUtf(12), buf.readUtf(KEY), buf.readVarInt(), buf.readBoolean(), buf.readUtf(REQUEST_ID)));

        public Roll {
            kind = s(kind);
            key = s(key);
            requestId = s(requestId);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * O formato do {@link Ask} no fio, fora do record: o record carrega o {@code ResourceLocation} do
     * canal ao inicializar, e o teste do codec roda sem o FML. {@code make} monta o resultado da leitura
     * (no jogo, {@code Ask::new}).
     */
    static final class AskWire {
        interface Make<T> {
            T make(List<UUID> targets, String kind, String skill, String attribute, int dc, boolean showDc, String mode,
                   boolean secret, String label, String formula);
        }

        private AskWire() {
        }

        static void write(FriendlyByteBuf buf, List<UUID> targets, String kind, String skill, String attribute, int dc, boolean showDc,
                          String mode, boolean secret, String label, String formula) {
            int count = Math.min(targets.size(), MAX_TABLE);
            buf.writeVarInt(count);
            for (int i = 0; i < count; i++) buf.writeUUID(u(targets.get(i)));
            utf(buf, kind, 12);
            utf(buf, skill, KEY);
            utf(buf, attribute, 8);
            buf.writeVarInt(dc);
            buf.writeBoolean(showDc);
            utf(buf, mode, 12);
            buf.writeBoolean(secret);
            utf(buf, label, LABEL);
            utf(buf, formula, FORMULA);
        }

        static <T> T read(FriendlyByteBuf buf, Make<T> make) {
            int count = count(buf, MAX_TABLE, "alvos");
            List<UUID> targets = new ArrayList<>(count);
            for (int i = 0; i < count; i++) targets.add(buf.readUUID());
            return make.make(targets, buf.readUtf(12), buf.readUtf(KEY), buf.readUtf(8), buf.readVarInt(), buf.readBoolean(),
                    buf.readUtf(12), buf.readBoolean(), buf.readUtf(LABEL), buf.readUtf(FORMULA));
        }
    }

    /**
     * O mestre pede um teste. {@code kind} {@code skill}/{@code attribute} ou {@code free} (dados:
     * {@code formula}, como {@code 2d6+3}; {@code skill}/{@code attribute} vazios e {@code mode}
     * {@code normal} — o site não aceita vantagem em rolagem livre). {@code attribute} numa perícia =
     * atributo alternativo (vazio = padrão). {@code mode} {@code normal}/{@code vantagem}/
     * {@code desvantagem}. No máximo {@link #MAX_TABLE} alvos; repetidos contam uma vez no servidor.
     *
     * <p>Protocolo 2: {@code formula} entrou no fim do pacote (a versão do canal subiu junto; o mod nunca
     * saiu com o formato antigo).
     */
    public record Ask(List<UUID> targets, String kind, String skill, String attribute, int dc, boolean showDc,
                      String mode, boolean secret, String label, String formula) implements CustomPacketPayload {
        public static final Type<Ask> TYPE = new Type<>(channel("ask"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Ask> STREAM_CODEC = StreamCodec.of(
                (buf, data) -> AskWire.write(buf, data.targets, data.kind, data.skill, data.attribute, data.dc, data.showDc, data.mode,
                        data.secret, data.label, data.formula),
                buf -> AskWire.read(buf, Ask::new));

        /** Teste de atributo ou perícia (sem fórmula). */
        public Ask(List<UUID> targets, String kind, String skill, String attribute, int dc, boolean showDc,
                   String mode, boolean secret, String label) {
            this(targets, kind, skill, attribute, dc, showDc, mode, secret, label, "");
        }

        /** Pedido de dados (rolagem livre): sem perícia, atributo nem vantagem. */
        public static Ask dice(List<UUID> targets, String formula, int dc, boolean showDc, boolean secret, String label) {
            return new Ask(targets, "free", "", "", dc, showDc, "normal", secret, label, formula);
        }

        public boolean free() {
            return "free".equals(kind);
        }

        public Ask {
            targets = targets == null ? List.of() : targets;
            kind = s(kind);
            skill = s(skill);
            attribute = s(attribute);
            mode = s(mode);
            label = s(label);
            formula = s(formula);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
