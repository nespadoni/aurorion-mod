package com.aurorion.servicos.server;

import com.aurorion.profissoes.data.Profession;
import com.aurorion.profissoes.data.ProfessionData;
import com.aurorion.servicos.data.Anuncio;
import com.aurorion.servicos.data.Categorias;
import com.aurorion.servicos.data.Pedido;
import com.aurorion.servicos.data.PedidoStatus;
import com.aurorion.servicos.data.ServicosData;
import com.aurorion.servicos.data.ServicosRules;
import com.aurorion.servicos.data.Vaga;
import com.aurorion.servicos.network.ServicosActionPayload;
import com.aurorion.servicos.network.ServicosNetwork;
import com.aurorion.servicos.network.ServicosNotifyPayload;
import com.aurorion.servicos.network.ServicosPagePayload;
import com.aurorion.servicos.network.ServicosPagePayload.Row;
import com.aurorion.servicos.network.ServicosQueryPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.aurorion.servicos.network.ServicosPagePayload.*;

/**
 * O servidor do app: cada acao e validada aqui, e toda resposta e a aba inteira de novo, ja com os
 * botoes que aquela pessoa pode usar em cada linha.
 *
 * <h2>O fluxo</h2>
 * <ul>
 *   <li><b>Anuncio</b>: ate {@value ServicosRules#MAX_ADS} por personagem, em qualquer area. Quem
 *       tem a profissao registrada pela staff naquela area ganha o selo e aparece antes.</li>
 *   <li><b>Trabalhando</b>: so em memoria, cai no logout. Quem esta trabalhando recebe no celular os
 *       pedidos abertos da area dos seus anuncios.</li>
 *   <li><b>Pedido direto</b>: a partir de um anuncio, com o profissional online. Ele e avisado, e quem
 *       pediu cai na conversa com ele no celular, com a mensagem ja escrita.</li>
 *   <li><b>Pedido aberto</b>: para a area inteira. O primeiro que aceitar leva (a thread do servidor
 *       e uma so, entao "o primeiro" e exato), e cai na conversa com o cliente.</li>
 *   <li><b>Vaga</b>: quem se candidata avisa o dono e cai na conversa com ele.</li>
 * </ul>
 *
 * <p>Nenhum dinheiro passa pelo app: preco e salario sao texto, e o acerto e RP (e PIX) na conversa.
 */
public final class ServicosManager {
    public static final String TAB_BUSCAR = "buscar";
    public static final String TAB_PEDIDOS = "pedidos";
    public static final String TAB_VAGAS = "vagas";
    public static final String TAB_PERFIL = "perfil";
    public static final String FILTER_MEUS = "meus";
    public static final String FILTER_RECEBIDOS = "recebidos";

    private static final long ACTION_COOLDOWN_MS = 400L;
    private static final long QUERY_COOLDOWN_MS = 150L;
    /** O limite do rascunho no chat do celular. */
    private static final int DRAFT = 180;

    private static final Set<UUID> ON_DUTY = new HashSet<>();
    private static final Map<UUID, Long> LAST_ACTION = new HashMap<>();
    private static final Map<UUID, Long> LAST_QUERY = new HashMap<>();
    /** Quem acabou de entrar e ainda vai ser lembrado dos pedidos, em ticks restantes. */
    private static final Map<UUID, Integer> LOGIN_REMINDERS = new HashMap<>();
    private static final int LOGIN_REMINDER_DELAY_TICKS = 100;

    private ServicosManager() {
    }

    private record Result(boolean ok, String message, String chatNick, String draft) {
        static Result done(String message) {
            return new Result(true, message, "", "");
        }

        static Result fail(String message) {
            return new Result(false, message, "", "");
        }

        static Result chat(String message, String nick, String draft) {
            return new Result(true, message, nick, cut(draft, DRAFT));
        }
    }

    // ---- entrada -------------------------------------------------------------------------------

    public static void query(ServerPlayer player, ServicosQueryPayload payload) {
        if (!cooldown(LAST_QUERY, player, QUERY_COOLDOWN_MS)) return;
        sendPage(player, payload.tab(), payload.filter(), Result.done(""));
    }

    public static void action(ServerPlayer player, ServicosActionPayload payload) {
        Result result;
        if (!cooldown(LAST_ACTION, player, ACTION_COOLDOWN_MS)) {
            result = Result.fail("Um toque de cada vez.");
        } else {
            result = switch (payload.action()) {
                case "anunciar" -> publishAd(player, payload);
                case "remover_anuncio" -> removeAd(player, payload.id());
                case "trabalhando" -> setWorking(player, "1".equals(payload.text()));
                case "pedir" -> orderDirect(player, payload);
                case "pedido_aberto" -> orderOpen(player, payload);
                case "aceitar" -> accept(player, payload.id());
                case "recusar" -> refuse(player, payload.id());
                case "concluir" -> complete(player, payload.id());
                case "cancelar" -> cancel(player, payload.id());
                case "vaga" -> publishJob(player, payload);
                case "fechar_vaga" -> closeJob(player, payload.id());
                case "candidatar" -> apply(player, payload.id());
                default -> Result.fail("Ação desconhecida.");
            };
        }
        sendPage(player, payload.tab(), payload.filter(), result);
    }

    // ---- anuncios e trabalho ------------------------------------------------------------------

    private static Result publishAd(ServerPlayer player, ServicosActionPayload payload) {
        ServicosData data = ServicosData.get(player.server);
        UUID me = player.getUUID();
        long now = System.currentTimeMillis();
        String category = payload.category();
        if (!Categorias.exists(category)) return Result.fail("Escolha uma área.");
        String title = ServicosRules.clean(payload.title(), ServicosRules.TITLE);
        if (title.isEmpty()) return Result.fail("Dê um título ao anúncio.");
        String description = ServicosRules.clean(payload.text(), ServicosRules.DESCRIPTION);
        String price = ServicosRules.clean(payload.price(), ServicosRules.PRICE);
        if (price.isEmpty()) price = "A combinar";

        if (payload.id() > 0) {
            Anuncio existing = data.ad(payload.id());
            if (existing == null || !existing.owner().equals(me)) return Result.fail("Esse anúncio não é seu.");
            data.put(existing.edit(category, title, description, price, now));
            return Result.done("Anúncio atualizado.");
        }
        if (data.adsOf(me).size() >= ServicosRules.MAX_ADS) {
            return Result.fail("Você já tem " + ServicosRules.MAX_ADS + " anúncios. Remova um para publicar outro.");
        }
        data.put(new Anuncio(data.nextId(), me, category, title, description, price, now, now));
        return Result.done("Anúncio publicado em " + Categorias.label(category) + ".");
    }

    private static Result removeAd(ServerPlayer player, long id) {
        ServicosData data = ServicosData.get(player.server);
        Anuncio ad = data.ad(id);
        if (ad == null) return Result.fail("Esse anúncio não existe mais.");
        boolean mine = ad.owner().equals(player.getUUID());
        if (!mine && !staff(player)) return Result.fail("Esse anúncio não é seu.");
        data.removeAd(id);
        if (data.adsOf(ad.owner()).isEmpty()) ON_DUTY.remove(ad.owner());
        if (!mine) {
            ServerPlayer owner = player.server.getPlayerList().getPlayer(ad.owner());
            if (owner != null) notify(owner, "Anúncio removido", "A staff removeu \"" + ad.title() + "\".");
        }
        return Result.done("Anúncio removido.");
    }

    private static Result setWorking(ServerPlayer player, boolean on) {
        ServicosData data = ServicosData.get(player.server);
        UUID me = player.getUUID();
        if (!on) {
            ON_DUTY.remove(me);
            return Result.done("Você parou de trabalhar. Pedidos abertos não chegam mais.");
        }
        if (data.adsOf(me).isEmpty()) return Result.fail("Publique um anúncio antes de começar a trabalhar.");
        ON_DUTY.add(me);
        int waiting = openInMyAreas(data, me).size();
        return Result.done(waiting > 0
                ? "Você está trabalhando. Há " + waiting + " pedido(s) esperando na sua área."
                : "Você está trabalhando. Pedidos da sua área chegam no celular.");
    }

    // ---- pedidos -------------------------------------------------------------------------------

    private static Result orderDirect(ServerPlayer player, ServicosActionPayload payload) {
        MinecraftServer server = player.server;
        ServicosData data = ServicosData.get(server);
        UUID me = player.getUUID();
        Anuncio ad = data.ad(payload.id());
        if (ad == null) return Result.fail("Esse anúncio não existe mais.");
        if (ad.owner().equals(me)) return Result.fail("Esse anúncio é seu.");
        String providerName = ServicosNames.name(server, ad.owner());
        ServerPlayer provider = server.getPlayerList().getPlayer(ad.owner());
        if (provider == null) {
            return Result.fail(providerName + " está offline. Tente outro profissional ou faça um pedido aberto.");
        }
        for (Pedido pedido : data.orders()) {
            if (pedido.requester().equals(me) && pedido.adId() == ad.id() && pedido.status() == PedidoStatus.ABERTO) {
                return Result.chat("Você já pediu esse serviço. Aguarde a resposta.", ServicosNames.nick(server, ad.owner()), "");
            }
        }
        if (data.openOrdersOf(me) >= ServicosRules.MAX_OPEN_ORDERS) {
            return Result.fail("Você já tem " + ServicosRules.MAX_OPEN_ORDERS + " pedidos aguardando. Cancele um ou espere.");
        }
        String text = ServicosRules.clean(payload.text(), ServicosRules.ORDER_TEXT);
        if (text.isEmpty()) text = "Quero contratar: " + ad.title();

        long now = System.currentTimeMillis();
        data.put(new Pedido(data.nextId(), me, ad.category(), text, ad.owner(), ad.id(), PedidoStatus.ABERTO, null, now, now));
        notify(provider, "Novo pedido de " + ServicosNames.name(server, me), text);
        return Result.chat("Pedido enviado para " + providerName + ". Combine os detalhes na conversa.",
                ServicosNames.nick(server, ad.owner()),
                "Olá! Vi seu anúncio \"" + ad.title() + "\" no Serviços. " + text);
    }

    private static Result orderOpen(ServerPlayer player, ServicosActionPayload payload) {
        MinecraftServer server = player.server;
        ServicosData data = ServicosData.get(server);
        UUID me = player.getUUID();
        String category = payload.category();
        if (!Categorias.exists(category)) return Result.fail("Escolha uma área.");
        String text = ServicosRules.clean(payload.text(), ServicosRules.ORDER_TEXT);
        if (text.isEmpty()) return Result.fail("Descreva o que você precisa.");
        if (data.openOrdersOf(me) >= ServicosRules.MAX_OPEN_ORDERS) {
            return Result.fail("Você já tem " + ServicosRules.MAX_OPEN_ORDERS + " pedidos aguardando. Cancele um ou espere.");
        }

        long now = System.currentTimeMillis();
        data.put(new Pedido(data.nextId(), me, category, text, null, 0, PedidoStatus.ABERTO, null, now, now));
        String label = Categorias.label(category);
        String myName = ServicosNames.name(server, me);
        int warned = 0;
        for (ServerPlayer other : server.getPlayerList().getPlayers()) {
            UUID account = other.getUUID();
            if (account.equals(me) || !ON_DUTY.contains(account) || !data.offers(account, category)) continue;
            notify(other, "Pedido aberto: " + label, myName + ": " + text);
            warned++;
        }
        return Result.done(warned > 0
                ? "Pedido publicado. " + warned + " profissional(is) trabalhando em " + label + " foram avisados."
                : "Pedido publicado. Ninguém de " + label + " está trabalhando agora; quem começar verá seu pedido.");
    }

    private static Result accept(ServerPlayer player, long id) {
        MinecraftServer server = player.server;
        ServicosData data = ServicosData.get(server);
        UUID me = player.getUUID();
        Pedido pedido = data.order(id);
        if (pedido == null) return Result.fail("Esse pedido não existe mais.");
        if (!ServicosRules.canAccept(pedido, me, data.offers(me, pedido.category()))) {
            return Result.fail(pedido.status() == PedidoStatus.ACEITO ? "Alguém já atendeu esse pedido." : "Você não pode aceitar esse pedido.");
        }
        data.put(pedido.with(PedidoStatus.ACEITO, me, System.currentTimeMillis()));

        String label = Categorias.label(pedido.category());
        ServerPlayer requester = server.getPlayerList().getPlayer(pedido.requester());
        if (requester != null) notify(requester, ServicosNames.name(server, me) + " aceitou seu pedido", label + ": " + pedido.text());
        String clientName = ServicosNames.name(server, pedido.requester());
        return Result.chat("Pedido aceito. Combine com " + clientName + " na conversa.",
                ServicosNames.nick(server, pedido.requester()),
                "Olá, " + clientName + "! Aceitei seu pedido de " + label + ": \"" + pedido.text() + "\". ");
    }

    private static Result refuse(ServerPlayer player, long id) {
        MinecraftServer server = player.server;
        ServicosData data = ServicosData.get(server);
        Pedido pedido = data.order(id);
        if (pedido == null) return Result.fail("Esse pedido não existe mais.");
        if (!ServicosRules.canRefuse(pedido, player.getUUID())) return Result.fail("Você não pode recusar esse pedido.");
        data.put(pedido.with(PedidoStatus.RECUSADO, pedido.provider(), System.currentTimeMillis()));
        ServerPlayer requester = server.getPlayerList().getPlayer(pedido.requester());
        if (requester != null) {
            notify(requester, ServicosNames.name(server, player.getUUID()) + " não pode atender",
                    "Tente outro profissional ou faça um pedido aberto.");
        }
        return Result.done("Pedido recusado.");
    }

    private static Result complete(ServerPlayer player, long id) {
        MinecraftServer server = player.server;
        ServicosData data = ServicosData.get(server);
        UUID me = player.getUUID();
        Pedido pedido = data.order(id);
        if (pedido == null) return Result.fail("Esse pedido não existe mais.");
        if (!ServicosRules.canComplete(pedido, me)) return Result.fail("Esse pedido não está em andamento.");
        data.put(pedido.with(PedidoStatus.CONCLUIDO, pedido.provider(), System.currentTimeMillis()));
        warnCounterpart(server, pedido, me, "Pedido concluído", ServicosNames.name(server, me) + " marcou o pedido como concluído.");
        return Result.done("Pedido concluído.");
    }

    private static Result cancel(ServerPlayer player, long id) {
        MinecraftServer server = player.server;
        ServicosData data = ServicosData.get(server);
        UUID me = player.getUUID();
        Pedido pedido = data.order(id);
        if (pedido == null) return Result.fail("Esse pedido não existe mais.");
        if (!ServicosRules.canCancel(pedido, me)) return Result.fail("Você não pode cancelar esse pedido.");
        data.put(pedido.with(PedidoStatus.CANCELADO, pedido.provider(), System.currentTimeMillis()));
        warnCounterpart(server, pedido, me, "Pedido cancelado", ServicosNames.name(server, me) + " cancelou o pedido.");
        return Result.done("Pedido cancelado.");
    }

    private static void warnCounterpart(MinecraftServer server, Pedido pedido, UUID me, String title, String text) {
        UUID other = pedido.counterpart(me);
        if (other == null) return;
        ServerPlayer online = server.getPlayerList().getPlayer(other);
        if (online != null) notify(online, title, text);
    }

    // ---- vagas ---------------------------------------------------------------------------------

    private static Result publishJob(ServerPlayer player, ServicosActionPayload payload) {
        ServicosData data = ServicosData.get(player.server);
        UUID me = player.getUUID();
        String category = payload.category();
        if (!Categorias.exists(category)) return Result.fail("Escolha uma área.");
        String title = ServicosRules.clean(payload.title(), ServicosRules.TITLE);
        if (title.isEmpty()) return Result.fail("Dê um título à vaga.");
        String description = ServicosRules.clean(payload.text(), ServicosRules.DESCRIPTION);
        String salary = ServicosRules.clean(payload.price(), ServicosRules.PRICE);
        if (salary.isEmpty()) salary = "A combinar";
        if (data.jobsOf(me).size() >= ServicosRules.MAX_JOBS) {
            return Result.fail("Você já tem " + ServicosRules.MAX_JOBS + " vagas abertas. Encerre uma para publicar outra.");
        }
        data.put(new Vaga(data.nextId(), me, category, title, description, salary, System.currentTimeMillis(), List.of()));
        return Result.done("Vaga publicada. Ela fica aberta por 14 dias.");
    }

    private static Result closeJob(ServerPlayer player, long id) {
        ServicosData data = ServicosData.get(player.server);
        Vaga vaga = data.job(id);
        if (vaga == null) return Result.fail("Essa vaga não existe mais.");
        if (!vaga.owner().equals(player.getUUID()) && !staff(player)) return Result.fail("Essa vaga não é sua.");
        data.removeJob(id);
        return Result.done("Vaga encerrada.");
    }

    private static Result apply(ServerPlayer player, long id) {
        MinecraftServer server = player.server;
        ServicosData data = ServicosData.get(server);
        UUID me = player.getUUID();
        Vaga vaga = data.job(id);
        if (vaga == null) return Result.fail("Essa vaga não existe mais.");
        if (vaga.owner().equals(me)) return Result.fail("Essa vaga é sua.");
        String ownerNick = ServicosNames.nick(server, vaga.owner());
        if (vaga.applied(me)) return Result.chat("Você já se candidatou. Continue a conversa.", ownerNick, "");
        if (vaga.candidates().size() >= ServicosRules.MAX_CANDIDATES) return Result.fail("Essa vaga já tem candidatos demais.");
        data.put(vaga.withCandidate(me));
        ServerPlayer owner = server.getPlayerList().getPlayer(vaga.owner());
        if (owner != null) notify(owner, "Novo candidato: " + ServicosNames.name(server, me), "Vaga: " + vaga.title());
        return Result.chat("Candidatura enviada. Apresente-se na conversa.", ownerNick,
                "Olá! Tenho interesse na vaga \"" + vaga.title() + "\" que vi no Serviços. ");
    }

    // ---- paginas -------------------------------------------------------------------------------

    private static void sendPage(ServerPlayer player, String tab, String filter, Result result) {
        MinecraftServer server = player.server;
        ServicosData data = ServicosData.get(server);
        UUID me = player.getUUID();
        List<Row> rows = switch (tab) {
            case TAB_PEDIDOS -> orderRows(server, data, me, filter);
            case TAB_VAGAS -> jobRows(server, data, me, staff(player), filter);
            case TAB_PERFIL -> profileRows(server, data, me);
            default -> adRows(server, data, me, staff(player), filter);
        };
        ServicosNetwork.send(player, new ServicosPagePayload(tab, filter, ON_DUTY.contains(me), !data.adsOf(me).isEmpty(),
                badge(data, me), result.ok(), cut(result.message(), 250), result.chatNick(), result.draft(), rows));
    }

    private static List<Row> adRows(MinecraftServer server, ServicosData data, UUID me, boolean staff, String category) {
        ProfessionData professions = ProfessionData.get(server);
        List<Anuncio> ads = new ArrayList<>();
        for (Anuncio ad : data.ads()) if (category.isEmpty() || ad.category().equals(category)) ads.add(ad);
        ads.sort(Comparator.<Anuncio>comparingInt(ad -> ON_DUTY.contains(ad.owner()) ? 0 : 1)
                .thenComparingInt(ad -> server.getPlayerList().getPlayer(ad.owner()) != null ? 0 : 1)
                .thenComparingInt(ad -> registered(professions, ad) ? 0 : 1)
                .thenComparing(Comparator.comparingLong(Anuncio::createdAt).reversed()));
        List<Row> rows = new ArrayList<>();
        for (Anuncio ad : ads) {
            if (rows.size() >= MAX_ROWS) break;
            rows.add(adRow(server, professions, ad, me, staff));
        }
        return rows;
    }

    private static Row adRow(MinecraftServer server, ProfessionData professions, Anuncio ad, UUID me, boolean staff) {
        boolean mine = ad.owner().equals(me);
        boolean online = server.getPlayerList().getPlayer(ad.owner()) != null;
        String nick = mine ? "" : ServicosNames.nick(server, ad.owner());
        int flags = 0;
        if (ON_DUTY.contains(ad.owner())) flags |= WORKING;
        if (online) flags |= ONLINE;
        if (registered(professions, ad)) flags |= REGISTERED;
        if (mine) flags |= MINE | CAN_EDIT;
        if (!mine && online) flags |= CAN_ORDER;
        if (mine || staff) flags |= CAN_REMOVE;
        if (!mine && !nick.isEmpty()) flags |= CAN_CHAT;
        return new Row(ad.id(), KIND_AD, flags, ad.category(), ad.title(), Categorias.label(ad.category()), ad.description(),
                ad.price(), ServicosNames.name(server, ad.owner()), nick, ad.createdAt(), 0, 0);
    }

    private static List<Row> orderRows(MinecraftServer server, ServicosData data, UUID me, String filter) {
        boolean incoming = FILTER_RECEBIDOS.equals(filter);
        List<Pedido> orders = new ArrayList<>();
        for (Pedido pedido : data.orders()) {
            boolean asClient = pedido.requester().equals(me);
            boolean asProvider = !asClient && (me.equals(pedido.target()) || me.equals(pedido.provider())
                    || (!pedido.direct() && pedido.status() == PedidoStatus.ABERTO && data.offers(me, pedido.category())));
            if (incoming ? asProvider : asClient) orders.add(pedido);
        }
        orders.sort(Comparator.<Pedido>comparingInt(pedido -> pedido.status().ordinal())
                .thenComparing(Comparator.comparingLong(Pedido::updatedAt).reversed()));
        List<Row> rows = new ArrayList<>();
        for (Pedido pedido : orders) {
            if (rows.size() >= MAX_ROWS) break;
            rows.add(orderRow(server, data, pedido, me));
        }
        return rows;
    }

    private static Row orderRow(MinecraftServer server, ServicosData data, Pedido pedido, UUID me) {
        boolean incoming = !pedido.requester().equals(me);
        UUID other = pedido.counterpart(me);
        String label = Categorias.label(pedido.category());
        String person = other != null ? ServicosNames.name(server, other) : "Aberto para " + label;
        String nick = other != null ? ServicosNames.nick(server, other) : "";
        int flags = 0;
        if (pedido.direct()) flags |= DIRECT;
        if (incoming) flags |= INCOMING;
        if (other != null && server.getPlayerList().getPlayer(other) != null) flags |= ONLINE;
        if (ServicosRules.canAccept(pedido, me, data.offers(me, pedido.category()))) flags |= CAN_ACCEPT;
        if (ServicosRules.canRefuse(pedido, me)) flags |= CAN_REFUSE;
        if (ServicosRules.canComplete(pedido, me)) flags |= CAN_COMPLETE;
        if (ServicosRules.canCancel(pedido, me)) flags |= CAN_CANCEL;
        if (!nick.isEmpty() && (pedido.status() != PedidoStatus.ABERTO || pedido.direct() || incoming)) flags |= CAN_CHAT;
        return new Row(pedido.id(), KIND_ORDER, flags, pedido.category(), label, pedido.status().label(), pedido.text(),
                "", person, nick, pedido.updatedAt(), 0, 0);
    }

    private static List<Row> jobRows(MinecraftServer server, ServicosData data, UUID me, boolean staff, String category) {
        List<Vaga> jobs = new ArrayList<>();
        for (Vaga vaga : data.jobs()) if (category.isEmpty() || vaga.category().equals(category)) jobs.add(vaga);
        jobs.sort(Comparator.comparingLong(Vaga::createdAt).reversed());
        List<Row> rows = new ArrayList<>();
        for (Vaga vaga : jobs) {
            if (rows.size() >= MAX_ROWS) break;
            rows.add(jobRow(server, vaga, me, staff));
        }
        return rows;
    }

    private static Row jobRow(MinecraftServer server, Vaga vaga, UUID me, boolean staff) {
        boolean mine = vaga.owner().equals(me);
        String nick = mine ? "" : ServicosNames.nick(server, vaga.owner());
        int flags = 0;
        if (server.getPlayerList().getPlayer(vaga.owner()) != null) flags |= ONLINE;
        if (mine) flags |= MINE;
        if (!mine && !vaga.applied(me)) flags |= CAN_APPLY;
        if (vaga.applied(me)) flags |= APPLIED;
        if (mine || staff) flags |= CAN_REMOVE;
        if (!mine && !nick.isEmpty()) flags |= CAN_CHAT;
        return new Row(vaga.id(), KIND_JOB, flags, vaga.category(), vaga.title(), Categorias.label(vaga.category()),
                vaga.description(), vaga.salary(), ServicosNames.name(server, vaga.owner()), nick, vaga.createdAt(), 0,
                vaga.candidates().size());
    }

    private static List<Row> profileRows(MinecraftServer server, ServicosData data, UUID me) {
        ProfessionData professions = ProfessionData.get(server);
        List<Row> rows = new ArrayList<>();
        for (Anuncio ad : data.adsOf(me)) rows.add(adRow(server, professions, ad, me, false));
        for (Vaga vaga : data.jobsOf(me)) {
            rows.add(jobRow(server, vaga, me, false));
            for (UUID candidate : vaga.candidates()) {
                if (rows.size() >= MAX_ROWS) break;
                String nick = ServicosNames.nick(server, candidate);
                int flags = nick.isEmpty() ? 0 : CAN_CHAT;
                if (server.getPlayerList().getPlayer(candidate) != null) flags |= ONLINE;
                rows.add(new Row(0, KIND_CANDIDATE, flags, vaga.category(), vaga.title(), "Candidato", "", "",
                        ServicosNames.name(server, candidate), nick, 0, vaga.id(), 0));
            }
        }
        return rows;
    }

    // ---- avisos --------------------------------------------------------------------------------

    static void notify(ServerPlayer target, String title, String text) {
        ServicosData data = ServicosData.get(target.server);
        ServicosNetwork.send(target, new ServicosNotifyPayload(cut(title, 90), cut(text, 250), badge(data, target.getUUID())));
    }

    /** O que espera uma resposta de {@code me}: pedido direto para ele e, trabalhando, os abertos da area. */
    static int badge(ServicosData data, UUID me) {
        int count = 0;
        boolean working = ON_DUTY.contains(me);
        for (Pedido pedido : data.orders()) {
            if (pedido.status() != PedidoStatus.ABERTO || pedido.requester().equals(me)) continue;
            if (me.equals(pedido.target())) count++;
            else if (working && !pedido.direct() && data.offers(me, pedido.category())) count++;
        }
        return count;
    }

    static List<Pedido> openInMyAreas(ServicosData data, UUID me) {
        List<Pedido> list = new ArrayList<>();
        for (Pedido pedido : data.orders()) {
            if (pedido.status() == PedidoStatus.ABERTO && !pedido.direct() && !pedido.requester().equals(me)
                    && data.offers(me, pedido.category())) {
                list.add(pedido);
            }
        }
        return list;
    }

    // ---- ciclo de vida -------------------------------------------------------------------------

    static void onLogin(ServerPlayer player) {
        ServicosData.get(player.server).touch(player.getUUID(), System.currentTimeMillis());
        // O aviso espera o inventario chegar ao cliente: e ele que diz se a pessoa esta com o celular.
        LOGIN_REMINDERS.put(player.getUUID(), LOGIN_REMINDER_DELAY_TICKS);
    }

    /** Chamado a cada tick; so trabalha nos poucos segundos depois de alguem entrar. */
    static void tickReminders(MinecraftServer server) {
        if (LOGIN_REMINDERS.isEmpty()) return;
        var it = LOGIN_REMINDERS.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            if (entry.getValue() > 1) {
                entry.setValue(entry.getValue() - 1);
                continue;
            }
            it.remove();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null) remindPending(player);
        }
    }

    private static void remindPending(ServerPlayer player) {
        ServicosData data = ServicosData.get(player.server);
        UUID me = player.getUUID();
        int direct = badge(data, me);
        int open = data.adsOf(me).isEmpty() ? 0 : openInMyAreas(data, me).size();
        if (direct > 0) {
            notify(player, "Serviços", "Você tem " + direct + " pedido(s) esperando resposta.");
        } else if (open > 0) {
            notify(player, "Serviços", "Há " + open + " pedido(s) abertos na sua área. Ligue o Trabalhando para atender.");
        }
    }

    static void onLogout(UUID account) {
        ON_DUTY.remove(account);
        LAST_ACTION.remove(account);
        LAST_QUERY.remove(account);
        LOGIN_REMINDERS.remove(account);
    }

    static void onReset(MinecraftServer server, UUID account) {
        ServicosData.get(server).forget(account);
        onLogout(account);
    }

    static void age(MinecraftServer server) {
        ServicosData data = ServicosData.get(server);
        for (Pedido pedido : data.age(System.currentTimeMillis(), account -> server.getPlayerList().getPlayer(account) != null)) {
            if (pedido.status() != PedidoStatus.EXPIRADO) continue;
            ServerPlayer requester = server.getPlayerList().getPlayer(pedido.requester());
            if (requester != null) {
                notify(requester, "Pedido expirado", "Ninguém atendeu seu pedido de " + Categorias.label(pedido.category()) + ".");
            }
        }
    }

    static void clear() {
        ON_DUTY.clear();
        LAST_ACTION.clear();
        LAST_QUERY.clear();
        LOGIN_REMINDERS.clear();
    }

    // ---- utilidades ----------------------------------------------------------------------------

    private static boolean registered(ProfessionData professions, Anuncio ad) {
        Profession profession = Categorias.profession(ad.category());
        return profession != null && professions.of(ad.owner()) == profession;
    }

    private static boolean staff(ServerPlayer player) {
        return player.hasPermissions(2);
    }

    private static boolean cooldown(Map<UUID, Long> last, ServerPlayer player, long interval) {
        long now = System.currentTimeMillis();
        Long previous = last.get(player.getUUID());
        if (previous != null && now - previous < interval) return false;
        last.put(player.getUUID(), now);
        return true;
    }

    private static String cut(@Nullable String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, max);
    }
}
