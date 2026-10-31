package com.aurorion.ficha.server;

import com.aurorion.core.character.AltData;
import com.aurorion.core.character.CharacterNamedEvent;
import com.aurorion.core.character.CharacterResetEvent;
import com.aurorion.core.diagnostics.ServerDiagnostics;
import com.aurorion.core.integration.GameFacts;
import com.aurorion.ficha.AurorionFicha;
import com.aurorion.ficha.config.FichaConfig;
import com.aurorion.ficha.network.FichaNetwork;
import com.aurorion.ficha.network.FichaPayloads;
import com.aurorion.ficha.sheet.Binding;
import com.aurorion.ficha.sheet.Candidate;
import com.aurorion.ficha.sheet.Catalog;
import com.aurorion.ficha.sheet.DiceFormula;
import com.aurorion.ficha.sheet.FichaSheet;
import com.aurorion.ficha.sheet.Json;
import com.aurorion.ficha.sheet.RollCard;
import com.aurorion.ficha.sheet.RollResult;
import com.aurorion.ficha.sheet.Texts;
import com.aurorion.integracao.bridge.FactBridge;
import com.aurorion.integracao.outbox.SiteApi;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.jetbrains.annotations.Nullable;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * O lado do servidor da {@code /ficha}: conversa com o site pelo {@link SiteApi} da integração e com
 * as telas e o HUD pelos pacotes da ficha.
 *
 * <h2>Custo</h2>
 * Nada roda por tick. Abrir a ficha, vincular, rolar e espiar são uma chamada assíncrona cada; a
 * resposta volta à thread do servidor com {@code server.execute} antes de tocar no jogador
 * ({@link #call}; resposta que chega depois de o servidor parar é descartada). Reabrir
 * a tela dentro de {@code cacheSegundos} mostra a cópia na hora; se ela já tem alguns segundos, uma
 * chamada em segundo plano confere a {@code version} do site e só manda a tela de novo se a ficha
 * mudou lá ({@link #revalidate}). O login faz uma chamada só, alguns segundos depois de entrar (ou
 * quando o personagem ganha nome, se ainda estava na criação): num alt, para avisar se ele não tem
 * ficha; na conta principal, para avisar a ficha antiga (sem a construção nova), no máximo uma vez
 * por dia por perfil — e a resposta já fica no cache para a primeira abertura. Pedidos do mestre
 * vencem sem relógio ({@link GmRequests}).
 *
 * <h2>Respostas que chegam tarde</h2>
 * Cada chamada guarda a conexão de quem pediu ({@link #back}): se o jogador saiu — ou saiu e
 * voltou, inclusive trocando de alt — a resposta não abre tela nem manda aviso para a sessão nova.
 * Só a rolagem segue para o mestre e os vizinhos, porque ela já foi gravada no site.
 *
 * <h2>Quem é quem</h2>
 * Perfil, conta dona e personagem vêm sempre da sessão no servidor ({@link GameFacts#subject},
 * {@link AltData#ownerOf}); a tela nunca diz de quem é a ficha. O site confere o vínculo a cada
 * chamada e decide qual ficha o perfil usa. Mestre no jogo = permissão local do perfil
 * ({@code permissaoMestre}) para abrir a mesa e pedir — o alt, que nasce sem OP, não é mestre;
 * vantagem, desvantagem e atributo alternativo ainda passam pela staff confirmada no site
 * ({@code gm_profile_uuid}/{@code gm_account_uuid}).
 *
 * <h2>Rolar de novo</h2>
 * Cada rolagem leva um {@code command_id} sorteado aqui. Sem resposta do site (rede, 5xx, 429), a
 * mesma chamada — o mesmo id — sai mais uma vez 1,5 s depois: o executor do site é idempotente e
 * devolve o resultado já gravado em vez de rolar duas vezes. Pedido do mestre usa o id do alvo,
 * sorteado na criação do pedido, então até apertar a tecla de novo é seguro.
 */
@EventBusSubscriber(modid = AurorionFicha.MOD_ID)
public final class FichaServer {
    private static final long MIN_REQUEST_INTERVAL_MS = 250L;
    private static final long RETRY_DELAY_MS = 1_500L;
    /** Cópia em cache mais velha que isto: mostra na hora e confere a versão no site em segundo plano. */
    private static final long REVALIDATE_AFTER_MS = 5_000L;
    /** {@code atualizar} vindo da tela aberta: no máximo uma conferida no site a cada 10 s por jogador. */
    private static final long REFRESH_INTERVAL_MS = 10_000L;
    /** Login de alt: espera o mundo carregar no cliente antes de avisar (e espalha a fila de logins). */
    private static final long ALT_CHECK_DELAY_MS = 4_000L;
    /** Alt que acabou de ganhar nome: a tela de criação fecha antes de o aviso aparecer. */
    private static final long NAMED_CHECK_DELAY_MS = 1_500L;
    /** O aviso "sem ficha" não se repete para o mesmo perfil dentro deste prazo (relogar, trocar e voltar). */
    private static final long WARN_AGAIN_MS = 10 * 60_000L;
    /**
     * Login da conta principal: a conferida da ficha antiga sai entre 5 e 8 s depois (o mundo já
     * carregou no cliente, e logins juntos — reinício do servidor — não viram uma rajada no site).
     */
    private static final long MAIN_CHECK_DELAY_MS = 5_000L;
    private static final int MAIN_CHECK_SPREAD_MS = 3_000;
    /** O aviso de ficha antiga sai no máximo uma vez por dia por perfil. */
    private static final long OUTDATED_AGAIN_MS = 24 * 60 * 60_000L;
    private static final List<String> MODES = List.of("normal", "vantagem", "desvantagem");
    private static final String INTEGRATION_OFF = "A ficha depende da integração com o site, que está desligada neste servidor.";

    // Tudo abaixo só é tocado na thread do servidor (pacotes com enqueueWork, comandos, eventos e as
    // respostas do site devolvidas por call()). Os mapas são concorrentes mesmo assim: o diagnóstico
    // e qualquer leitura futura fora dessa thread não pegam um HashMap no meio de um rehash.
    private static final Map<UUID, Map<String, Long>> LAST_REQUEST = new ConcurrentHashMap<>();
    /**
     * Uma rolagem por jogador viajando (perfil → {@code command_id}): clique duplo não vira duas
     * chamadas. A resposta só solta a trava que ela mesma pôs — quem saiu e voltou com outra rolagem
     * a caminho não perde a trava nova quando a velha responde.
     */
    private static final Map<UUID, String> ROLLING = new ConcurrentHashMap<>();
    /** Perfis com a conferida de login agendada ou a caminho: login + "ganhou nome" não viram duas chamadas. */
    private static final Set<UUID> CHECKING = ConcurrentHashMap.newKeySet();
    /** Perfis com uma conferida de versão a caminho ({@link #revalidate}). */
    private static final Set<UUID> REFRESHING = ConcurrentHashMap.newKeySet();
    /** Perfis com a busca de abrir a ficha a caminho: a tecla da ficha repetida não vira outra chamada. */
    private static final Set<UUID> OPENING = ConcurrentHashMap.newKeySet();
    /**
     * Perfil → quando recebeu o aviso "sem ficha". Sobrevive ao logout de propósito (é o que segura o
     * aviso ao relogar); entradas vencidas saem a cada aviso novo, então o mapa tem no máximo os alts
     * avisados nos últimos {@link #WARN_AGAIN_MS}.
     */
    private static final Map<UUID, Long> WARNED = new ConcurrentHashMap<>();
    /**
     * Perfil (conta principal) → quando leu o aviso de ficha antiga. Como {@link #WARNED}, sobrevive ao
     * logout; vencidas saem a cada aviso novo. Só memória: reiniciar o servidor pode repetir o aviso
     * no mesmo dia, o que é aceitável para um lembrete.
     */
    private static final Map<UUID, Long> OUTDATED_WARNED = new ConcurrentHashMap<>();
    /** Até quando a mesa confia na cópia da ficha para marcar "sem ficha"/"sem vínculo" (é só uma dica). */
    private static final long TABLE_HINT_MS = 30 * 60_000L;
    /** Erro de configuração (credencial, rota ausente) entra no log uma vez por servidor, não a cada clique. */
    private static volatile boolean setupWarned;
    /**
     * {@code sheet}/{@code npc} da última ficha vista do perfil. Só reforço: quem decide se o bônus
     * é privado é o {@code roll.private} do site ({@link RollAudience#privateBonus}).
     */
    private static final Map<UUID, String> SUBJECT_TYPE = new ConcurrentHashMap<>();
    private static final SheetCache<SheetData> CACHE = new SheetCache<>(System::currentTimeMillis);
    private static final GmRequests REQUESTS = new GmRequests(new SecureRandom());

    private static final AtomicLong CALLS = new AtomicLong();
    private static final AtomicLong FAILURES = new AtomicLong();
    private static final AtomicLong ROLLS = new AtomicLong();
    private static volatile String lastResult = "nenhuma chamada";

    @Nullable
    private static volatile MinecraftServer server;

    /** Uma resposta de {@code /rpg/sheet} (ou {@code /bind}, {@code /peek}) já conferida. */
    record SheetData(String code, @Nullable FichaSheet sheet, @Nullable Binding binding, List<Candidate> candidates) {
    }

    /** Uma rolagem a fazer. {@code key}: perícia, atributo ou fórmula, conforme {@code kind}. */
    record RollSpec(String kind, String key, String attribute, String label, int dc, boolean showDc, boolean secret,
                    String mode, @Nullable UUID gm) {
        String test() {
            return Texts.testName(kind, key, attribute);
        }
    }

    private FichaServer() {
    }

    // ── Ciclo de vida ───────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onStarted(ServerStartedEvent event) {
        stop();
        server = event.getServer();
        ServerDiagnostics.register("Ficha", FichaServer::diagnostics);
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent event) {
        stop();
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        LAST_REQUEST.remove(id);
        ROLLING.remove(id);
        CHECKING.remove(id);
        REFRESHING.remove(id);
        OPENING.remove(id);
        SUBJECT_TYPE.remove(id);
        CACHE.invalidate(id);
        GmRequests.Left left = REQUESTS.forget(id);
        // O mestre saiu: os cartões dos pedidos dele somem do HUD de quem ainda não respondeu (e quem
        // joga sem o mod lê que o pedido acabou, em vez de clicar num [Rolar] morto).
        for (GmRequests.Request request : left.dropped()) requestGone(request, "cancelado", "terminou: o mestre saiu do servidor", false);
        // Um alvo saiu: a mesa de quem pediu fica sabendo, em vez de esperar até o prazo.
        for (GmRequests.Request request : left.abandoned()) tellGm(request, id, "erro", "saiu do servidor", null);
    }

    /**
     * Login (entrar ou {@code /personagem trocar}, que reconecta): uma chamada só, alguns segundos
     * depois. Num alt, para avisar se esse personagem ainda não tem ficha; na conta principal, se a
     * ficha ativa ainda é antiga ({@link #scheduleMainCheck}). Personagem ainda na criação (sem nome)
     * espera {@link #onNamed}: o aviso por cima da tela de criação só atrapalharia.
     */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (GameFacts.subject(player.server, player.getUUID()).characterName().isEmpty()) return;
        if (isAlt(player)) scheduleAltCheck(player, ALT_CHECK_DELAY_MS);
        else scheduleMainCheck(player, MAIN_CHECK_DELAY_MS + java.util.concurrent.ThreadLocalRandom.current().nextInt(MAIN_CHECK_SPREAD_MS));
        scheduleRenewal(player);
    }

    /** Personagem novo: o retrato da ficha do anterior não decide mais teste nenhum. */
    @SubscribeEvent
    public static void onReset(CharacterResetEvent event) {
        SheetStatsStore.forget(event.server(), event.account());
    }

    /**
     * Releitura do retrato com a pessoa online, a cada {@link FichaConfig#SNAPSHOT_RENEW_MINUTES}: a ficha
     * mudada no site chega aos testes do jogo sem ela abrir a tela. Uma corrente por conexão (a sessão
     * nova de um relogin começa a dela; a velha morre no {@link #back}), sem relógio por tick.
     */
    private static void scheduleRenewal(ServerPlayer player) {
        int minutes = FichaConfig.get(FichaConfig.SNAPSHOT_RENEW_MINUTES);
        if (minutes <= 0 || FactBridge.site().isEmpty()) return;
        UUID id = player.getUUID();
        Object session = player.connection;
        later(minutes * 60_000L + java.util.concurrent.ThreadLocalRandom.current().nextInt(MAIN_CHECK_SPREAD_MS), () -> {
            ServerPlayer online = back(id, session);
            if (online == null) return;
            Optional<SiteApi> site = FactBridge.site();
            if (site.isPresent() && !CHECKING.contains(id)) {
                String characterId = characterKey(online);
                call(site.get(), "/rpg/sheet", identity(online), 0, response -> {
                    if (!response.transportFailure() && back(id, session) != null) remember(id, characterId, sheetData(response));
                });
            }
            scheduleRenewal(online);
        });
    }

    /**
     * O personagem ganhou nome (criação, renascimento ou renomeado pela staff): o subject muda, então
     * a cópia em cache sai; é a hora da conferida que o login adiou (a marca de {@link #CHECKING}
     * segura a repetição de um renascimento logo depois do login).
     */
    @SubscribeEvent
    public static void onNamed(CharacterNamedEvent event) {
        ServerPlayer player = event.player();
        CACHE.invalidate(player.getUUID());
        if (isAlt(player)) scheduleAltCheck(player, NAMED_CHECK_DELAY_MS);
        else scheduleMainCheck(player, NAMED_CHECK_DELAY_MS);
    }

    /**
     * Conta principal: uma chamada a {@code /rpg/sheet} depois do login para avisar a ficha antiga
     * (sem a construção nova; com a construção enviada e aguardando aprovação não há o que fazer, e
     * não avisa). Nada se o aviso está desligado, se o perfil já foi avisado nas últimas 24 h ou se
     * outra conferida já está agendada. A resposta entra no cache: abrir a ficha logo depois não chama
     * o site de novo.
     */
    private static void scheduleMainCheck(ServerPlayer player, long delayMs) {
        if (FactBridge.site().isEmpty()) return;
        UUID id = player.getUUID();
        Long warnedAt = OUTDATED_WARNED.get(id);
        boolean warn = FichaConfig.get(FichaConfig.WARN_OUTDATED)
                && (warnedAt == null || System.currentTimeMillis() - warnedAt >= OUTDATED_AGAIN_MS);
        // O retrato dos testes (magia, ofício) também precisa desta leitura, avisando ou não.
        if (!warn && !FichaConfig.get(FichaConfig.SNAPSHOT_ON_LOGIN)) return;
        if (!CHECKING.add(id)) return;
        Object session = player.connection;
        later(delayMs, () -> {
            ServerPlayer online = back(id, session);
            if (online == null) CHECKING.remove(id);
            else mainCheck(online);
        });
    }

    private static void mainCheck(ServerPlayer player) {
        UUID id = player.getUUID();
        Optional<SiteApi> site = FactBridge.site();
        if (site.isEmpty()) {
            CHECKING.remove(id);
            return;
        }
        String characterId = characterKey(player);
        // Abriu a ficha antes da conferida: a cópia que acabou de vir do site já responde.
        SheetData cached = CACHE.get(id, characterId, cacheTtlMs());
        if (cached != null) {
            CHECKING.remove(id);
            checkOutdated(player, site.get(), cached);
            return;
        }
        Object session = player.connection;
        call(site.get(), "/rpg/sheet", identity(player), 0, response -> {
            CHECKING.remove(id);
            ServerPlayer online = back(id, session);
            if (online == null || response.transportFailure()) return;
            SheetData data = sheetData(response);
            remember(id, characterId, data);
            checkOutdated(online, site.get(), data);
        });
    }

    private static void checkOutdated(ServerPlayer player, SiteApi site, SheetData data) {
        if (!FichaConfig.get(FichaConfig.WARN_OUTDATED)) return;
        FichaSheet sheet = data.sheet();
        if ("ok".equals(data.code()) && sheet != null && !sheet.hasBuild() && !sheet.pendingReview()) warnOutdated(player, site, sheet);
    }

    /**
     * Ficha antiga na conta principal: no mod, um aviso no HUD ("aperte &lt;tecla&gt; para ver" — o
     * cliente põe o nome da tecla que ele configurou no lugar de {@link Texts#KEY_PLACEHOLDER}); sem o mod,
     * uma linha no chat com o link do site. Uma vez por perfil a cada {@link #OUTDATED_AGAIN_MS}.
     */
    private static void warnOutdated(ServerPlayer player, SiteApi site, FichaSheet sheet) {
        UUID id = player.getUUID();
        long now = System.currentTimeMillis();
        OUTDATED_WARNED.values().removeIf(at -> now - at >= OUTDATED_AGAIN_MS);
        if (OUTDATED_WARNED.putIfAbsent(id, now) != null) return;
        if (FichaNetwork.canOpen(player)) {
            FichaNetwork.send(player, new FichaPayloads.Notice("Ficha antiga", clip(Texts.OUTDATED_NOTICE, FichaPayloads.MESSAGE), 12));
            return;
        }
        player.sendSystemMessage(ChatCards.text("Sua ficha ainda não foi atualizada para o sistema novo: monte a construção no site"
                + " e envie para aprovar (a rolagem livre, /ficha dados, já funciona).", ChatCards.WARN));
        player.sendSystemMessage(ChatCards.siteLink("Atualizar no site: ", siteUrl(site, sheet)));
    }


    private static void scheduleAltCheck(ServerPlayer player, long delayMs) {
        if (FactBridge.site().isEmpty()) return;
        if (!FichaConfig.get(FichaConfig.WARN_ALT) && !FichaConfig.get(FichaConfig.SNAPSHOT_ON_LOGIN)) return;
        UUID id = player.getUUID();
        if (!CHECKING.add(id)) return;
        Object session = player.connection;
        later(delayMs, () -> {
            ServerPlayer online = back(id, session);
            if (online == null) CHECKING.remove(id);
            else altCheck(online);
        });
    }

    private static void altCheck(ServerPlayer player) {
        UUID id = player.getUUID();
        Optional<SiteApi> site = FactBridge.site();
        if (site.isEmpty()) {
            CHECKING.remove(id);
            return;
        }
        Object session = player.connection;
        String characterId = characterKey(player);
        call(site.get(), "/rpg/sheet", identity(player), 0, response -> {
            CHECKING.remove(id);
            ServerPlayer online = back(id, session);
            if (online == null || response.transportFailure()) return;
            SheetData data = sheetData(response);
            remember(id, characterId, data);
            if ("needs_binding".equals(data.code()) && FichaConfig.get(FichaConfig.WARN_ALT)) warnAltWithoutSheet(online, site.get(), data);
        });
    }

    private static void stop() {
        ServerDiagnostics.unregister("Ficha");
        LAST_REQUEST.clear();
        ROLLING.clear();
        CHECKING.clear();
        REFRESHING.clear();
        OPENING.clear();
        WARNED.clear();
        OUTDATED_WARNED.clear();
        setupWarned = false;
        SUBJECT_TYPE.clear();
        CACHE.clear();
        REQUESTS.clear();
        server = null;
    }

    private static List<String> diagnostics() {
        List<String> lines = new ArrayList<>();
        lines.add("Transporte do site disponível: " + (FactBridge.site().isPresent() ? "sim" : "não"));
        lines.add("Chamadas ao site: " + CALLS.get() + "; sem resposta: " + FAILURES.get() + "; última: " + lastResult);
        lines.add("Rolagens concluídas: " + ROLLS.get() + "; em andamento agora: " + ROLLING.size());
        MinecraftServer current = server;
        if (current != null) lines.add("Retratos de ficha guardados (testes de magia e ofício): " + SheetStatsStore.get(current).size());
        lines.add("Fichas em cache: " + CACHE.size() + " (vale " + FichaConfig.get(FichaConfig.CACHE_SECONDS) + " s; conferidas de versão a caminho: "
                + REFRESHING.size() + ")");
        lines.add("Login: conferidas a caminho: " + CHECKING.size() + "; alts sem ficha avisados nos últimos 10 min: " + WARNED.size()
                + "; fichas antigas avisadas nas últimas 24 h: " + OUTDATED_WARNED.size()
                + (FichaConfig.get(FichaConfig.WARN_OUTDATED) ? "" : " (aviso de ficha antiga desligado)"));
        lines.add("Pedidos do mestre em memória: " + REQUESTS.size() + " (vencem em "
                + FichaConfig.get(FichaConfig.REQUEST_SECONDS) + " s, limpos ao criar outro)");
        lines.add("Raio público: " + FichaConfig.get(FichaConfig.PUBLIC_RADIUS) + " blocos; mesa do mestre: "
                + FichaConfig.get(FichaConfig.TABLE_RADIUS) + " blocos; permissão de mestre: " + FichaConfig.get(FichaConfig.GM_PERMISSION));
        return lines;
    }

    // ── Pacotes da tela ─────────────────────────────────────────────────────────

    public static void action(ServerPlayer player, FichaPayloads.Action payload) {
        switch (payload.action()) {
            case "abrir" -> open(player, false);
            // A tela aberta pergunta se a ficha mudou no site; a = a versão que ela mostra
            // (sheet.version; sem ficha, "code:" + código — o mesmo de versionKey).
            case "atualizar" -> refresh(player, payload.a());
            case "candidatos" -> candidates(player);
            case "vincular" -> {
                long id = parseLong(payload.b());
                if (("sheet".equals(payload.a()) || "npc".equals(payload.a())) && id > 0) bind(player, payload.a(), id);
            }
            case "limpar" -> clearBinding(player);
            case "mestre" -> openTable(player);
            case "espiar" -> {
                UUID target = parseUuid(payload.a());
                if (target != null) peek(player, target);
            }
            case "recusar" -> decline(player, payload.a());
            case "cancelar" -> cancelRequest(player, payload.a());
            default -> { }
        }
    }

    public static void roll(ServerPlayer player, FichaPayloads.Roll payload) {
        if ("pedido".equals(payload.kind())) {
            respond(player, payload.requestId());
            return;
        }
        if (!acceptRequest(player, "rolar")) {
            status(player, "rolar", "ocupado", "Uma rolagem de cada vez: espere um instante.");
            return;
        }
        RollSpec spec = playerSpec(payload.kind(), payload.key(), payload.dc(), payload.secret());
        if (spec == null) {
            // Rolagem livre recusada: o motivo e o formato aceito (a tela mostra no diálogo).
            String why = "free".equals(payload.kind()) ? DiceFormula.explain(payload.key()) : null;
            status(player, "rolar", "erro", why != null ? why : "Teste desconhecido. Escolha uma perícia, um atributo ou uma fórmula como 2d6+3.");
            return;
        }
        execute(player, spec, null, UUID.randomUUID().toString());
    }

    /** O que um jogador pode rolar sozinho: sem vantagem, desvantagem nem atributo alternativo. */
    @Nullable
    static RollSpec playerSpec(String kind, String key, int dc, boolean secret) {
        int target = dc > 0 && dc <= 99 ? dc : 0;
        return switch (kind) {
            case "skill" -> {
                Catalog.Skill skill = Catalog.skill(key);
                yield skill == null ? null : new RollSpec("skill", skill.id(), "", "", target, true, secret, "normal", null);
            }
            case "attribute" -> {
                Catalog.Attribute attribute = Catalog.attribute(key);
                yield attribute == null ? null : new RollSpec("attribute", attribute.id(), "", "", target, true, secret, "normal", null);
            }
            case "free" -> {
                // A gramática do motor (DiceFormula): fórmula que o site recusaria nem sai daqui.
                String formula = DiceFormula.normalize(key);
                yield formula == null ? null : new RollSpec("free", formula, "", "", 0, true, secret, "normal", null);
            }
            default -> null;
        };
    }

    // ── /ficha ──────────────────────────────────────────────────────────────────

    /** Abre a ficha: tela para quem tem o mod; resumo, link e comandos no chat para quem não tem. */
    public static void open(ServerPlayer player, boolean bindTab) {
        if (!acceptRequest(player, "abrir")) return;
        Optional<SiteApi> site = FactBridge.site();
        if (site.isEmpty()) {
            // Status (e não só chat): no mod, o "Abrindo a ficha…" do HUD fecha na hora em vez de esperar o prazo.
            status(player, "abrir", "erro", INTEGRATION_OFF);
            return;
        }
        String characterId = characterKey(player);
        UUID id = player.getUUID();
        SheetData cached = CACHE.get(id, characterId, cacheTtlMs());
        if (cached != null) {
            deliver(player, site.get(), cached, bindTab);
            // A tela abre na hora com a cópia; se ela já tem uns segundos, o site confere a versão e
            // a tela troca sozinha caso a ficha tenha mudado lá (build aprovada, recursos, vínculo).
            if (FichaNetwork.canOpen(player) && CACHE.ageMs(id) >= REVALIDATE_AFTER_MS) revalidate(player, site.get(), versionKey(cached));
            return;
        }
        // A tecla da ficha apertada de novo enquanto a primeira busca viaja: a resposta dela já abre a tela.
        if (!OPENING.add(id)) return;
        Object session = player.connection;
        call(site.get(), "/rpg/sheet", identity(player), 0, response -> {
            OPENING.remove(id);
            ServerPlayer online = back(id, session);
            if (online == null) return;
            if (response.transportFailure()) {
                failure(online, "abrir", response);
                return;
            }
            SheetData data = sheetData(response);
            remember(id, characterId, data);
            deliver(online, site.get(), data, bindTab);
        });
    }

    /**
     * A tela aberta pergunta "a ficha mudou no site?" ({@code atualizar}, com a versão que ela
     * mostra). No máximo uma conferida a cada {@link #REFRESH_INTERVAL_MS} por jogador; dentro disso a
     * cópia em cache responde. Só manda a tela de novo quando a versão mudou — sem mudança, nada vai
     * pela rede.
     */
    public static void refresh(ServerPlayer player, String knownVersion) {
        if (!FichaNetwork.canOpen(player) || !acceptRequest(player, "atualizar", REFRESH_INTERVAL_MS)) return;
        Optional<SiteApi> site = FactBridge.site();
        if (site.isEmpty()) return;
        UUID id = player.getUUID();
        SheetData cached = CACHE.get(id, characterKey(player), cacheTtlMs());
        String known = Json.clean(knownVersion, 64);
        if (known.isEmpty() && cached != null) known = versionKey(cached);
        if (cached != null && CACHE.ageMs(id) < REFRESH_INTERVAL_MS) {
            if (!versionKey(cached).equals(known)) pushChanged(player, site.get(), cached);
            else if (HabilidadesColeta.changed(player)) pushAbilities(player, site.get(), cached);
            return;
        }
        revalidate(player, site.get(), known);
    }

    /** Busca a ficha em segundo plano e manda a tela de novo só se a versão for outra que {@code known}. */
    private static void revalidate(ServerPlayer player, SiteApi site, String known) {
        UUID id = player.getUUID();
        if (!REFRESHING.add(id)) return;
        Object session = player.connection;
        String characterId = characterKey(player);
        call(site, "/rpg/sheet", identity(player), 0, response -> {
            REFRESHING.remove(id);
            ServerPlayer online = back(id, session);
            // Erro aqui fica calado: a tela já mostra uma ficha válida, e o próximo clique avisa.
            if (online == null || response.transportFailure()) return;
            SheetData fresh = sheetData(response);
            if (!cacheable(fresh.code())) return;
            remember(id, characterId, fresh);
            if (!versionKey(fresh).equals(known)) pushChanged(online, site, fresh);
            else if (HabilidadesColeta.changed(online)) pushAbilities(online, site, fresh);
        });
    }

    /**
     * A ficha nova para uma tela que já está aberta: sem {@code OPEN}, o cliente só atualiza a ficha
     * que estiver na tela (fechada, ignora). Vai para a aba Vínculo só se agora falta escolher ficha.
     */
    private static void pushChanged(ServerPlayer player, SiteApi site, SheetData data) {
        boolean choose = "needs_binding".equals(data.code()) || "no_sheet".equals(data.code());
        int flags = (isGm(player) ? FichaPayloads.GM : 0) | (isAlt(player) ? FichaPayloads.ALT : 0) | (choose ? FichaPayloads.TAB_BIND : 0);
        String message = choose ? codeMessage(data.code()) : "A ficha mudou no site; esta é a versão de agora.";
        FichaNetwork.send(player, new FichaPayloads.View(flags, data.code(), viewJson(data, player, null),
                clip(siteUrl(site, data.sheet()), 300), clip(message, FichaPayloads.MESSAGE)));
    }

    /**
     * A ficha do site é a mesma, mas as habilidades no jogo mudaram (magia liberada em aula, passiva
     * ligada): a mesma View sem {@code OPEN}, na aba em que a tela estiver.
     */
    private static void pushAbilities(ServerPlayer player, SiteApi site, SheetData data) {
        int flags = (isGm(player) ? FichaPayloads.GM : 0) | (isAlt(player) ? FichaPayloads.ALT : 0);
        FichaNetwork.send(player, new FichaPayloads.View(flags, data.code(), viewJson(data, player, null),
                clip(siteUrl(site, data.sheet()), 300), clip("Habilidades do jogo atualizadas.", FichaPayloads.MESSAGE)));
    }

    /**
     * O que identifica "a mesma tela": a {@code version} do site (hash do conteúdo da ficha); sem
     * ficha, o código. Backend antigo sem {@code version} cai no hash do modelo já lido.
     */
    static String versionKey(SheetData data) {
        FichaSheet sheet = data.sheet();
        if (sheet == null) return "code:" + data.code();
        return sheet.version().isEmpty() ? "hash:" + Integer.toHexString(sheet.hashCode()) : sheet.version();
    }

    private static long cacheTtlMs() {
        return FichaConfig.get(FichaConfig.CACHE_SECONDS) * 1000L;
    }

    private static void deliver(ServerPlayer player, SiteApi site, SheetData data, boolean bindTab) {
        if (FichaNetwork.canOpen(player)) {
            int flags = FichaPayloads.OPEN | (isGm(player) ? FichaPayloads.GM : 0) | (isAlt(player) ? FichaPayloads.ALT : 0)
                    | (bindTab || "needs_binding".equals(data.code()) || "no_sheet".equals(data.code()) ? FichaPayloads.TAB_BIND : 0);
            FichaNetwork.send(player, new FichaPayloads.View(flags, data.code(), viewJson(data, player, null),
                    clip(siteUrl(site, data.sheet()), 300), clip(codeMessage(data.code()), FichaPayloads.MESSAGE)));
            return;
        }
        // Cliente sem o mod: o chat faz o papel da tela.
        switch (data.code()) {
            case "ok" -> {
                if (data.sheet() != null) ChatCards.sheetSummary(data.sheet()).forEach(player::sendSystemMessage);
                // Vínculo explícito velho com a ficha padrão em uso (só acontece na conta principal).
                if (data.binding() != null && data.binding().stale()) {
                    player.sendSystemMessage(ChatCards.info("A ficha escolhida antes para este perfil deixou de valer; vale a sua"
                            + " ficha ativa. /ficha vincular para escolher outra."));
                }
                player.sendSystemMessage(ChatCards.commandHint(vanillaCommands()));
                player.sendSystemMessage(ChatCards.siteLink("Ficha completa no site: ", siteUrl(site, data.sheet())));
            }
            case "needs_binding", "no_sheet" -> {
                player.sendSystemMessage(ChatCards.info(codeMessage(data.code())));
                listCandidates(player, data.candidates(), data.binding(), null);
            }
            default -> player.sendSystemMessage(ChatCards.info(codeMessage(data.code())));
        }
    }

    private static Map<String, String> vanillaCommands() {
        Map<String, String> out = new LinkedHashMap<>();
        out.put("/ficha rolar ", "Rolar uma perícia ou atributo: /ficha rolar furtividade 15");
        out.put("/ficha dados ", "Rolagem livre: /ficha dados 2d6+3");
        out.put("/ficha vincular", "Escolher qual ficha este perfil usa");
        return out;
    }

    // ── Vínculo ─────────────────────────────────────────────────────────────────

    /** {@code /ficha vincular}: a tela na aba Vínculo, ou a lista clicável no chat. */
    public static void chooseBinding(ServerPlayer player) {
        if (FichaNetwork.canOpen(player)) open(player, true);
        else candidates(player);
    }

    public static void candidates(ServerPlayer player) {
        if (!acceptRequest(player, "candidatos")) return;
        Optional<SiteApi> site = FactBridge.site();
        if (site.isEmpty()) {
            // A aba Vínculo destrava o "Atualizar" com o motivo, em vez de esperar o prazo da tela.
            status(player, "vincular", "erro", INTEGRATION_OFF);
            return;
        }
        UUID id = player.getUUID();
        Object session = player.connection;
        call(site.get(), "/rpg/candidates", identity(player), 0, response -> {
            ServerPlayer online = back(id, session);
            if (online == null) return;
            if (response.transportFailure()) {
                failure(online, "vincular", response);
                return;
            }
            String code = codeOf(response);
            List<Candidate> list = Candidate.parseList(arrayOf(response, "candidates"));
            Binding binding = Binding.parse(response.object("binding"));
            if (FichaNetwork.canOpen(online)) {
                JsonObject json = new JsonObject();
                json.addProperty("code", code);
                json.add("candidates", Candidate.toJson(list));
                json.add("binding", binding == null ? JsonNull.INSTANCE : binding.toJson());
                FichaNetwork.send(online, new FichaPayloads.Candidates(fit(json, FichaPayloads.CANDIDATES_JSON)));
                // not_linked/foreign: a lista veio vazia; o motivo vai por extenso para a tela.
                if (!"ok".equals(code)) FichaNetwork.send(online, new FichaPayloads.Status("vincular", "erro",
                        clip(codeMessage(code), FichaPayloads.MESSAGE)));
            } else if ("ok".equals(code)) {
                listCandidates(online, list, binding, null);
            } else {
                online.sendSystemMessage(ChatCards.info(codeMessage(code)));
            }
        });
    }

    /**
     * A lista clicável do vanilla. {@code inUse}: a ficha que o perfil usa agora (quando veio junto);
     * sem ela, vale o vínculo explícito que ainda não ficou velho. Um vínculo velho ({@code stale})
     * pode conviver com a ficha padrão em uso na conta principal — por isso a marca vem da ficha.
     */
    private static void listCandidates(ServerPlayer player, List<Candidate> list, @Nullable Binding binding,
                                       @Nullable FichaSheet inUse) {
        if (binding != null && binding.stale()) {
            player.sendSystemMessage(ChatCards.info("A ficha escolhida antes para este perfil deixou de valer (não é mais sua"
                    + " ou o NPC foi desligado de você)." + (inUse != null ? " Por enquanto vale a sua ficha ativa." : "")));
        }
        if (list.isEmpty()) {
            player.sendSystemMessage(ChatCards.info("Nenhuma ficha disponível. Crie ou ative uma ficha no site."));
            if (binding != null && binding.explicit()) player.sendSystemMessage(ChatCards.clearLine());
            return;
        }
        player.sendSystemMessage(ChatCards.text("Fichas que este perfil pode usar:", ChatCards.GOLD));
        int shown = 0;
        for (Candidate c : list) {
            if (shown++ >= 12) {
                player.sendSystemMessage(ChatCards.info("   … e mais " + (list.size() - 12) + ". A lista completa está na tela do mod ou no site."));
                break;
            }
            boolean current = inUse != null ? c.sameAs(inUse.subjectType(), inUse.subjectId())
                    : binding != null && !binding.stale() && c.sameAs(binding.subjectType(), binding.subjectId());
            player.sendSystemMessage(ChatCards.candidateLine(c, current));
        }
        if (binding != null && binding.explicit()) player.sendSystemMessage(ChatCards.clearLine());
    }

    public static void bind(ServerPlayer player, String subjectType, long subjectId) {
        JsonObject body = identity(player);
        body.addProperty("subject_type", subjectType);
        body.addProperty("subject_id", subjectId);
        sendBind(player, body, "Ficha vinculada a este perfil.");
    }

    public static void clearBinding(ServerPlayer player) {
        JsonObject body = identity(player);
        body.addProperty("clear", true);
        sendBind(player, body, "Vínculo removido: este perfil volta ao padrão.");
    }

    private static void sendBind(ServerPlayer player, JsonObject body, String done) {
        if (!acceptRequest(player, "vincular")) return;
        Optional<SiteApi> site = FactBridge.site();
        if (site.isEmpty()) {
            status(player, "vincular", "erro", INTEGRATION_OFF);
            return;
        }
        UUID id = player.getUUID();
        String characterId = characterKey(player);
        Object session = player.connection;
        call(site.get(), "/rpg/bind", body, 0, response -> {
            // O vínculo vale para o perfil mesmo que a pessoa tenha saído: a cópia velha sai de qualquer jeito.
            CACHE.invalidate(id);
            ServerPlayer online = back(id, session);
            if (online == null) return;
            if (response.transportFailure()) {
                failure(online, "vincular", response);
                return;
            }
            SheetData data = sheetData(response);
            String code = data.code();
            boolean modded = FichaNetwork.canOpen(online);
            // O vínculo foi gravado (ou apagado); o código é o da resolução que vale agora: limpar
            // num alt volta a needs_binding, limpar na principal sem ficha ativa dá no_sheet.
            if (cacheable(code)) {
                // Escolheu ficha: o aviso "sem ficha" do login já não se aplica a este perfil.
                if ("ok".equals(code)) WARNED.remove(id);
                remember(id, characterId, data);
                String message = "ok".equals(code) ? done : done + " " + codeMessage(code);
                if (modded) {
                    int flags = FichaPayloads.TAB_BIND | (isGm(online) ? FichaPayloads.GM : 0) | (isAlt(online) ? FichaPayloads.ALT : 0);
                    FichaNetwork.send(online, new FichaPayloads.View(flags, code, viewJson(data, online, null),
                            clip(siteUrl(site.get(), data.sheet()), 300), clip(message, FichaPayloads.MESSAGE)));
                    FichaNetwork.send(online, new FichaPayloads.Status("vincular", "ok", clip(message, FichaPayloads.MESSAGE)));
                } else {
                    online.sendSystemMessage(ChatCards.text("✔ " + message, ChatCards.OK));
                    if (data.sheet() != null) ChatCards.sheetSummary(data.sheet()).forEach(online::sendSystemMessage);
                    else if (!data.candidates().isEmpty()) listCandidates(online, data.candidates(), data.binding(), null);
                }
                return;
            }
            String detail = siteDetail(response);
            String message = switch (code) {
                // O site explica qual regra barrou (ficha de outra pessoa, NPC de outro jogador, NPC livre só pela staff).
                case "forbidden" -> "O site não deixou usar esta ficha" + (detail.isEmpty() ? "." : ": " + detail + ".")
                        + " Escolha outra da lista.";
                case "not_found" -> "Essa ficha não existe mais no site. Atualize a lista e escolha outra.";
                default -> codeMessage(code);
            };
            if (modded) FichaNetwork.send(online, new FichaPayloads.Status("vincular", "erro", clip(message, FichaPayloads.MESSAGE)));
            else online.sendSystemMessage(ChatCards.error(message));
            // A lista que a pessoa via estava velha (NPC levado por outra staff, ficha arquivada):
            // a nova chega sozinha, na tela ou no chat, sem ela precisar pedir.
            if ("forbidden".equals(code) || "not_found".equals(code)) candidates(online);
        });
    }

    /**
     * O alt entrou sem ficha: aviso no HUD, a aba Vínculo já com os candidatos (o cliente abre sozinho
     * se o jogador estiver sem outra tela — {@link FichaPayloads#PROMPT}) e a linha clicável no chat. Uma vez por
     * perfil a cada {@link #WARN_AGAIN_MS}: relogar ou trocar de alt e voltar não repete.
     */
    private static void warnAltWithoutSheet(ServerPlayer player, SiteApi site, SheetData data) {
        UUID id = player.getUUID();
        long now = System.currentTimeMillis();
        WARNED.values().removeIf(at -> now - at >= WARN_AGAIN_MS);
        if (WARNED.putIfAbsent(id, now) != null) return;
        String text = FichaConfig.get(FichaConfig.ALT_WITHOUT_SHEET).strip();
        if (FichaNetwork.canOpen(player)) {
            // No HUD a tecla de abrir aparece embaixo do texto: o ":" de "escolha:" ficaria pendurado.
            String hud = text.endsWith(":") ? text.substring(0, text.length() - 1).strip() + "." : text;
            FichaNetwork.send(player, new FichaPayloads.Notice("Sem ficha", clip(hud, FichaPayloads.MESSAGE), 14));
            int flags = FichaPayloads.PROMPT | FichaPayloads.TAB_BIND | FichaPayloads.ALT | (isGm(player) ? FichaPayloads.GM : 0);
            FichaNetwork.send(player, new FichaPayloads.View(flags, data.code(), viewJson(data, player, null),
                    clip(siteUrl(site, data.sheet()), 300), clip(codeMessage(data.code()), FichaPayloads.MESSAGE)));
        }
        player.sendSystemMessage(ChatCards.text(text + " ", ChatCards.SOFT)
                .append(ChatCards.button("[Escolher ficha]", ChatCards.GOLD, "/ficha vincular", "Ver as fichas e NPCs que você pode usar")));
    }

    // ── Rolagens ────────────────────────────────────────────────────────────────

    /** {@code /ficha rolar}: perícia ou atributo pelo id, com CD opcional. */
    public static void rollCommand(ServerPlayer player, String test, int dc, boolean secret) {
        if (!acceptRequest(player, "rolar")) return;
        Catalog.Skill skill = Catalog.skill(test);
        RollSpec spec = skill != null ? playerSpec("skill", skill.id(), dc, secret)
                : Catalog.attribute(test) != null ? playerSpec("attribute", test, dc, secret) : null;
        if (spec == null) {
            player.sendSystemMessage(ChatCards.error("Perícia ou atributo desconhecido: " + test + ". Use o TAB para ver a lista."));
            return;
        }
        execute(player, spec, null, UUID.randomUUID().toString());
    }

    public static void freeRollCommand(ServerPlayer player, String formula, boolean secret) {
        if (!acceptRequest(player, "rolar")) return;
        RollSpec spec = playerSpec("free", formula, 0, secret);
        if (spec == null) {
            String why = DiceFormula.explain(formula);
            player.sendSystemMessage(ChatCards.error(why != null ? why : "Fórmula inválida. Use NdF±bônus, como " + DiceFormula.EXAMPLES + "."));
            return;
        }
        execute(player, spec, null, UUID.randomUUID().toString());
    }

    private static void execute(ServerPlayer player, RollSpec spec, @Nullable GmRequests.Request request, String commandId) {
        Optional<SiteApi> site = FactBridge.site();
        UUID id = player.getUUID();
        if (site.isEmpty()) {
            if (request != null) {
                REQUESTS.release(request.id(), id);
                tellGm(request, id, "aguardando", "a integração com o site está desligada", null);
            }
            status(player, "rolar", "erro", INTEGRATION_OFF);
            return;
        }
        if (ROLLING.putIfAbsent(id, commandId) != null) {
            if (request != null) {
                // A mesa já mostrava "rolando": volta a "aguardando", senão fica presa até o prazo.
                REQUESTS.release(request.id(), id);
                tellGm(request, id, "aguardando", "outra rolagem dele estava a caminho; pode responder de novo", null);
            }
            status(player, "rolar", "ocupado", "Sua rolagem anterior ainda está a caminho. Espere o resultado e tente de novo.");
            return;
        }
        JsonObject body = identity(player);
        body.addProperty("command_id", commandId);
        body.addProperty("kind", spec.kind());
        switch (spec.kind()) {
            case "skill" -> {
                body.addProperty("skill", spec.key());
                if (!spec.attribute().isEmpty()) body.addProperty("attribute", spec.attribute());
            }
            case "attribute" -> body.addProperty("attribute", spec.key());
            default -> body.addProperty("formula", spec.key());
        }
        // O site só usa label na rolagem livre (o motivo do mestre fica no histórico de lá). Na livre a CD
        // compara o total de cada repetição lá; site antigo só a validava, e o onRoll compara (against).
        boolean free = "free".equals(spec.kind());
        if (free && !spec.label().isEmpty()) body.addProperty("label", spec.label());
        if (spec.dc() > 0) body.addProperty("dc", spec.dc());
        if (spec.secret()) body.addProperty("secret", true);
        if ("vantagem".equals(spec.mode())) body.addProperty("advantage", true);
        if ("desvantagem".equals(spec.mode())) body.addProperty("disadvantage", true);
        if (spec.gm() != null) {
            body.addProperty("gm_profile_uuid", spec.gm().toString());
            body.addProperty("gm_account_uuid", accountOf(player.server, spec.gm()).toString());
        }
        String rollerName = displayName(player);
        // Mesmo corpo, mesmo command_id: repetir depois de uma falha de rede é seguro.
        call(site.get(), "/rpg/roll", body, 1, response -> onRoll(id, rollerName, spec, request, commandId, response));
    }

    /**
     * O pedido viaja junto (e não só o id): se ele vencer enquanto o site responde, o mestre ainda
     * recebe o resultado e a CD escondida continua escondida.
     */
    private static void onRoll(UUID rollerId, String rollerName, RollSpec spec, @Nullable GmRequests.Request request,
                               String commandId, SiteApi.Response response) {
        ROLLING.remove(rollerId, commandId);
        ServerPlayer roller = online(rollerId);
        String code = codeOf(response);
        JsonObject rollJson = "ok".equals(code) ? response.object("roll") : null;
        RollResult roll = RollResult.parse(rollJson);
        if (roll != null) {
            ROLLS.incrementAndGet();
            CACHE.invalidate(rollerId);
            roll = roll.withLabel(spec.test());
            // Pedido de dados: o cartão mostra a fórmula pedida (o motivo mandado como label é do
            // histórico do site, não dos vizinhos) e a CD do mestre compara o total.
            if (request != null && "free".equals(spec.kind())) roll = roll.relabeled(spec.test()).against(spec.dc());
            String character = Json.str(response.body(), "character_name", FichaSheet.MAX_NAME);
            boolean dcHidden = spec.dc() > 0 && !spec.showDc();
            boolean hiddenBonus = RollAudience.privateBonus(roll.kind(), Json.bool(rollJson, "private"), isNpc(rollerId));
            RollCard gmCard = broadcast(roller, rollerId, rollerName, character, roll, spec, request, commandId, dcHidden, hiddenBonus);
            if (request != null) {
                // "17 · sucesso"; com repetição, "17 · 9 · 1 de 2 sucessos".
                String summary = Texts.resultSummary(roll);
                REQUESTS.finish(request.id(), rollerId, GmRequests.State.DONE, summary);
                tellGm(request, rollerId, "rolou", summary, gmCard);
                if (roller != null) closeRequestCard(roller, request.id());
            }
            return;
        }
        if ("ok".equals(code)) code = "invalid"; // "ok" sem rolagem legível
        // Sem resposta (rede, 5xx, 503 unavailable, 429) ou busy (rolagens demais em seguida, ou o
        // personagem está num encontro do site): o alvo pode responder de novo, com o mesmo command_id.
        boolean busy = "busy".equals(code);
        boolean retryable = response.transportFailure() || busy || "unavailable".equals(code);
        String message = rollMessage(code, response);
        // A mesa fala do jogador ("está num alt que ainda não escolheu ficha"), não com ele ("escolha qual ficha").
        String gmDetail = switch (code) {
            case "not_linked", "needs_binding", "no_sheet", "foreign" -> peekMessage(code, rollerName);
            // Ficha antiga: o mestre lê o caminho que funciona agora (Dados não usa a construção).
            // (com o motivo do site, ex.: recursos aguardando revisão, ele entra no lugar de "ficha antiga").
            case "no_build" -> siteDetail(response).isEmpty()
                    ? rollerName + " ainda está com a ficha antiga (sem a construção nova aprovada): peça Dados, que funciona."
                    : rollerName + " não pode rolar este teste: " + siteDetail(response) + ". Dados (rolagem livre) funcionam.";
            default -> message;
        };
        if (request != null) {
            if (retryable && roller != null) {
                REQUESTS.release(request.id(), rollerId);
                tellGm(request, rollerId, "aguardando", busy ? "o site segurou a rolagem (" + busyReason(response)
                        + "); pode responder de novo" : "o site não respondeu; pode responder de novo", null);
            } else if (retryable) {
                // Saiu antes de o site responder: "pode responder de novo" seria esperar por quem não volta.
                REQUESTS.finish(request.id(), rollerId, GmRequests.State.FAILED, "saiu do servidor");
                tellGm(request, rollerId, "erro", "saiu do servidor antes de o site responder", null);
            } else {
                REQUESTS.finish(request.id(), rollerId, GmRequests.State.FAILED, gmDetail);
                tellGm(request, rollerId, "erro", gmDetail, null);
                if (roller != null) closeRequestCard(roller, request.id());
            }
        }
        if (roller == null) return;
        status(roller, "rolar", retryable ? "ocupado" : "erro", message);
        // Sem ficha para rolar: o caminho para escolher uma chega junto (no mod, a tecla da ficha já
        // abre na aba Vínculo; sem o mod, a linha clicável).
        if ("needs_binding".equals(code) || "no_sheet".equals(code)) {
            CACHE.invalidate(rollerId);
            if (!FichaNetwork.canOpen(roller)) {
                roller.sendSystemMessage(ChatCards.text("   ", ChatCards.FAINT)
                        .append(ChatCards.button("[Escolher ficha]", ChatCards.GOLD, "/ficha vincular", "Ver as fichas e NPCs que você pode usar")));
            } else {
                // A tela aberta ainda mostra a ficha que deixou de valer: a resolução nova (com os
                // candidatos) vai sem OPEN e leva ao Vínculo; tela fechada ignora. Uma chamada, só aqui.
                Optional<SiteApi> site = FactBridge.site();
                if (site.isPresent()) revalidate(roller, site.get(), "");
            }
        }
    }

    /**
     * Entrega o cartão e a linha de chat a quem pode ver ({@link RollAudience}). Devolve o cartão
     * como o mestre do pedido o vê, para a mesa dele.
     */
    @Nullable
    private static RollCard broadcast(@Nullable ServerPlayer roller, UUID rollerId, String rollerName, String character,
                                      RollResult roll, RollSpec spec, @Nullable GmRequests.Request request, String commandId,
                                      boolean dcHidden, boolean hiddenBonus) {
        MinecraftServer current = server;
        if (current == null) return null;
        UUID gm = request == null ? null : request.gm();
        Map<UUID, String> audience;
        if (roller != null) {
            List<RollAudience.Viewer> viewers = new ArrayList<>();
            for (ServerPlayer p : current.getPlayerList().getPlayers()) viewers.add(viewer(p));
            audience = RollAudience.of(viewer(roller), viewers, spec.secret(), gm, FichaConfig.get(FichaConfig.PUBLIC_RADIUS));
        } else {
            // Quem rolou saiu antes da resposta: só o mestre do pedido recebe.
            audience = new LinkedHashMap<>();
            if (gm != null) audience.put(gm, RollCard.GM);
        }
        // O cartão só depende do jeito de ver (own/gm/public): no máximo três montagens e três JSON por
        // rolagem, não um por vizinho.
        Map<String, Shown> byView = new HashMap<>(4);
        RollCard gmCard = null;
        for (Map.Entry<UUID, String> entry : audience.entrySet()) {
            ServerPlayer viewer = current.getPlayerList().getPlayer(entry.getKey());
            Shown shown = byView.computeIfAbsent(entry.getValue(), view -> {
                boolean hideBonus = RollAudience.hideBonus(view, hiddenBonus);
                boolean hideTarget = RollAudience.hideTarget(view, dcHidden);
                RollCard card = new RollCard(commandId, rollerId.toString(), rollerName, character, view, spec.secret(),
                        request == null ? "" : request.id(), hideTarget || (dcHidden && RollCard.GM.equals(view)), hideBonus,
                        roll.redacted(hideBonus, hideTarget));
                return new Shown(card, new FichaPayloads.Card(clip(Json.write(card.toJson()), FichaPayloads.CARD_JSON)), ChatCards.rollLine(card));
            });
            if (entry.getKey().equals(gm)) gmCard = shown.card();
            if (viewer == null) continue;
            FichaNetwork.send(viewer, shown.packet());
            viewer.sendSystemMessage(shown.line());
        }
        return gmCard;
    }

    /** Um cartão pronto para um jeito de ver: o modelo (para a mesa), o pacote e a linha de chat. */
    private record Shown(RollCard card, FichaPayloads.Card packet, Component line) {
    }

    private static RollAudience.Viewer viewer(ServerPlayer p) {
        return new RollAudience.Viewer(p.getUUID(), p.level().dimension().location().toString(), p.getX(), p.getY(), p.getZ(), isGm(p));
    }

    /** Sem ficha vista ainda, um alt conta como NPC: na dúvida, o bônus não vaza. */
    private static boolean isNpc(UUID profile) {
        String type = SUBJECT_TYPE.get(profile);
        if (type != null) return "npc".equals(type);
        MinecraftServer current = server;
        return current != null && AltData.get(current).isAlt(profile);
    }

    // ── Pedidos do mestre ───────────────────────────────────────────────────────

    /** {@code /ficha mestre} ou a tecla: abre a mesa com os jogadores por perto. */
    public static void openTable(ServerPlayer gm) {
        if (!acceptRequest(gm, "mestre")) return;
        if (!isGm(gm)) {
            status(gm, "mestre", "erro", notGmMessage(gm));
            return;
        }
        if (!FichaNetwork.canOpen(gm)) {
            gm.sendSystemMessage(ChatCards.text("Mesa do mestre pelo chat:", ChatCards.GOLD));
            Map<String, String> commands = new LinkedHashMap<>();
            commands.put("/ficha mestre pedir ", "Pedir um teste ou dados: /ficha mestre pedir <jogadores> <perícia|atributo|2d6> [cd]");
            commands.put("/ficha mestre cancelar ", "Tirar um pedido de quem ainda não respondeu: /ficha mestre cancelar <id>");
            commands.put("/ficha mestre ver ", "Ver a ficha de um jogador: /ficha mestre ver <jogador>");
            gm.sendSystemMessage(ChatCards.commandHint(commands));
            return;
        }
        int radius = FichaConfig.get(FichaConfig.TABLE_RADIUS);
        double max = (double) radius * radius;
        List<ServerPlayer> near = new ArrayList<>();
        for (ServerPlayer p : gm.server.getPlayerList().getPlayers()) {
            if (p == gm || p.level() != gm.level() || p.distanceToSqr(gm) > max) continue;
            near.add(p);
        }
        near.sort(Comparator.comparingDouble(p -> p.distanceToSqr(gm)));
        List<FichaPayloads.Player> players = new ArrayList<>();
        for (ServerPlayer p : near) {
            if (players.size() >= FichaPayloads.MAX_TABLE) break;
            players.add(new FichaPayloads.Player(p.getUUID(), clip(p.getGameProfile().getName(), 16),
                    clip(displayName(p), FichaPayloads.NAME), (int) Math.round(Math.sqrt(p.distanceToSqr(gm))), FichaNetwork.canOpen(p),
                    knownSheet(p)));
        }
        FichaNetwork.send(gm, new FichaPayloads.Table(players, radius, FichaConfig.get(FichaConfig.REQUEST_SECONDS)));
    }

    /** O que a mesa mostra da ficha de um jogador, só com o que já está em memória (nunca chama o site). */
    private static String knownSheet(ServerPlayer player) {
        SheetData data = CACHE.get(player.getUUID(), characterKey(player), TABLE_HINT_MS);
        if (data != null) {
            return switch (data.code()) {
                case "ok" -> data.sheet() != null && !data.sheet().hasBuild() ? "sem_construcao" : "ok";
                case "needs_binding" -> "sem_vinculo";
                case "no_sheet" -> "sem_ficha";
                default -> "";
            };
        }
        return WARNED.containsKey(player.getUUID()) ? "sem_vinculo" : "";
    }

    /** O mestre pede um teste (pela mesa ou por {@code /ficha mestre pedir}). */
    public static void ask(ServerPlayer gm, FichaPayloads.Ask payload) {
        if (!acceptRequest(gm, "pedir")) {
            // A mesa trava o "Enviar" até chegar um status: sem este ela esperaria o prazo dela.
            status(gm, "mestre", "ocupado", "Um pedido de cada vez: espere um instante e envie de novo.");
            return;
        }
        if (!isGm(gm)) {
            status(gm, "mestre", "erro", notGmMessage(gm));
            return;
        }
        String kind = payload.kind();
        String key;
        String attribute = "";
        if ("skill".equals(kind)) {
            Catalog.Skill skill = Catalog.skill(payload.skill());
            key = skill == null ? null : skill.id();
            Catalog.Attribute alt = payload.attribute().isEmpty() ? null : Catalog.attribute(payload.attribute());
            if (alt != null && skill != null && !alt.id().equals(skill.attribute())) attribute = alt.id();
        } else if ("attribute".equals(kind)) {
            Catalog.Attribute a = Catalog.attribute(payload.attribute().isEmpty() ? payload.skill() : payload.attribute());
            key = a == null ? null : a.id();
        } else if (GmRequests.FREE.equals(kind)) {
            key = DiceFormula.normalize(payload.formula());
            if (key == null) {
                String why = DiceFormula.explain(payload.formula());
                status(gm, "mestre", "erro", why != null ? why : "Fórmula de dados inválida. Use NdF±bônus, como " + DiceFormula.EXAMPLES + ".");
                return;
            }
        } else {
            key = null;
        }
        if (key == null) {
            status(gm, "mestre", "erro", "Escolha uma perícia, um atributo ou uma fórmula de dados.");
            return;
        }
        String mode = MODES.contains(payload.mode()) ? payload.mode() : "normal";
        int dc = payload.dc() > 0 && payload.dc() <= 99 ? payload.dc() : 0;
        Map<UUID, String> targets = new LinkedHashMap<>();
        for (UUID target : payload.targets()) {
            ServerPlayer p = gm.server.getPlayerList().getPlayer(target);
            if (p != null) targets.put(target, displayName(p));
        }
        if (targets.isEmpty()) {
            status(gm, "mestre", "erro", "Nenhum dos jogadores escolhidos está online.");
            return;
        }
        String label = Json.clean(payload.label(), 60);
        GmRequests.Spec spec = GmRequests.FREE.equals(kind)
                ? GmRequests.Spec.dice(key, dc, payload.showDc() || dc == 0, payload.secret(), label)
                : new GmRequests.Spec(kind, key, attribute, dc, payload.showDc() || dc == 0, mode, payload.secret(), label);
        sendRequest(gm, spec, targets);
    }

    /**
     * {@code /ficha mestre pedir}: o mesmo pedido, montado pelo comando. {@code test}: perícia, atributo
     * ou uma fórmula de dados ({@code 2d6}, {@code 1d20+2}; também pelo ramo {@code dados <fórmula>}).
     */
    public static void askCommand(ServerPlayer gm, Collection<ServerPlayer> players, String test, int dc) {
        askCommand(gm, players, test, dc, false);
    }

    /** {@code diceOnly}: veio do ramo {@code dados <fórmula>} — só fórmula vale. */
    public static void askCommand(ServerPlayer gm, Collection<ServerPlayer> players, String test, int dc, boolean diceOnly) {
        if (!acceptRequest(gm, "pedir")) return;
        if (!isGm(gm)) {
            gm.sendSystemMessage(ChatCards.error(notGmMessage(gm)));
            return;
        }
        Catalog.Skill skill = diceOnly ? null : Catalog.skill(test);
        Catalog.Attribute attribute = diceOnly || skill != null ? null : Catalog.attribute(test);
        String formula = skill == null && attribute == null ? DiceFormula.normalize(test) : null;
        if (skill == null && attribute == null && formula == null) {
            // Tem cara de dado (um dígito colado no "d"): o motivo da fórmula ajuda mais que "desconhecido".
            gm.sendSystemMessage(ChatCards.error(diceOnly || looksLikeDice(test)
                    ? DiceFormula.explain(test)
                    : "Perícia, atributo ou fórmula de dados desconhecida: " + test + ". Use o TAB (ou 2d6, 1d20+2)."));
            return;
        }
        Map<UUID, String> targets = new LinkedHashMap<>();
        for (ServerPlayer p : players) targets.put(p.getUUID(), displayName(p));
        int cd = Math.max(0, Math.min(99, dc));
        GmRequests.Spec spec = formula != null ? GmRequests.Spec.dice(formula, cd, true, false, "")
                : new GmRequests.Spec(skill != null ? "skill" : "attribute", skill != null ? skill.id() : attribute.id(),
                "", cd, true, "normal", false, "");
        sendRequest(gm, spec, targets);
    }

    /** "2d6", "d20", "1d%": um dígito (ou o começo) colado no "d" — o mestre quis dados. */
    static boolean looksLikeDice(String text) {
        String t = text.toLowerCase(Locale.ROOT);
        int d = t.indexOf('d');
        return d >= 0 && (d == 0 || Character.isDigit(t.charAt(d - 1))) && d + 1 < t.length()
                && (Character.isDigit(t.charAt(d + 1)) || t.charAt(d + 1) == '%');
    }

    private static void sendRequest(ServerPlayer gm, GmRequests.Spec spec, Map<UUID, String> targets) {
        long now = System.currentTimeMillis();
        int seconds = FichaConfig.get(FichaConfig.REQUEST_SECONDS);
        // Os que saem para abrir espaço: vencidos (a mesa marca "venceu") ou o mais antigo além do teto.
        GmRequests.Request request = REQUESTS.create(gm.getUUID(), displayName(gm), spec, targets, now, seconds * 1000L,
                old -> {
                    if (old.expired(now)) requestGone(old, "expirou", "venceu", true);
                    else requestGone(old, "cancelado", "saiu: o mestre abriu pedidos demais ao mesmo tempo", true);
                });
        String test = spec.test();
        String detail = requestDetail(spec);
        String title = FichaConfig.get(FichaConfig.REQUEST_TITLE);
        boolean gmModded = FichaNetwork.canOpen(gm);
        for (GmRequests.Target target : request.targets().values()) {
            ServerPlayer p = gm.server.getPlayerList().getPlayer(target.id());
            if (p == null) continue;
            if (FichaNetwork.canOpen(p)) {
                FichaNetwork.send(p, new FichaPayloads.Request(request.id(), clip(request.gmName(), FichaPayloads.NAME),
                        clip(title, FichaPayloads.LABEL), clip(test, FichaPayloads.LABEL), clip(detail, 160), seconds, true));
            } else {
                p.sendSystemMessage(ChatCards.requestLine(title, request, test, detail));
            }
            if (gmModded) {
                FichaNetwork.send(gm, new FichaPayloads.TableUpdate(request.id(), clip(test, FichaPayloads.LABEL), target.id(),
                        clip(target.name(), FichaPayloads.NAME), "aguardando", "", ""));
            }
        }
        if (gmModded) {
            int count = request.targets().size();
            status(gm, "mestre", "ok", "Pedido " + request.id() + " (" + test + ") enviado a " + count + (count == 1 ? " jogador." : " jogadores."));
        } else {
            gm.sendSystemMessage(ChatCards.requestSentLine(request, test, "/ficha mestre cancelar " + request.id()));
        }
    }

    /**
     * O mestre desiste do pedido (botão da mesa ou {@code /ficha mestre cancelar <id>}). Quem já rolou
     * ou está rolando não muda: a resposta a caminho ainda chega à mesa.
     */
    public static void cancelRequest(ServerPlayer gm, String requestId) {
        GmRequests.Request request = REQUESTS.cancel(requestId, gm.getUUID(), System.currentTimeMillis());
        if (request == null) {
            status(gm, "mestre", "erro", "Esse pedido não está mais aberto: venceu, já foi cancelado ou é de outro mestre.");
            return;
        }
        requestGone(request, "cancelado", "foi cancelado pelo mestre", true);
        String done = "Pedido " + request.id() + " cancelado para quem ainda não respondeu.";
        if (FichaNetwork.canOpen(gm)) status(gm, "mestre", "ok", done);
        else gm.sendSystemMessage(ChatCards.text("✔ " + done, ChatCards.OK));
    }

    /**
     * O pedido saiu (cancelado, vencido, mestre saiu, teto de pedidos): o cartão some do HUD de quem
     * ainda não tinha respondido, quem joga sem o mod lê o motivo e a mesa marca cada um. Vencido não
     * vai para o chat do alvo: o prazo ele já viu passar, e o aviso chegaria só quando o mestre
     * criasse outro pedido.
     */
    private static void requestGone(GmRequests.Request request, String state, String reason, boolean tellTable) {
        String test = request.spec().test();
        boolean expired = "expirou".equals(state);
        for (GmRequests.Target target : request.waiting()) {
            ServerPlayer p = online(target.id());
            if (p != null) {
                // No HUD o motivo vira um aviso curto (o cartão sumir sozinho não explica nada); vencido
                // não: o prazo correu à vista no próprio cartão.
                if (FichaNetwork.canOpen(p)) closeRequestCard(p, request.id(), expired ? "" : test, expired ? "" : reason);
                else if (!expired) p.sendSystemMessage(ChatCards.requestGoneLine(request.id(), test, reason));
            }
            if (tellTable) tellGm(request, target.id(), state, "", null);
        }
    }

    /** "CD 15 · com vantagem · secreto · ouvir a porta" — a CD escondida não entra aqui. */
    static String requestDetail(GmRequests.Spec spec) {
        List<String> parts = new ArrayList<>();
        if (spec.dc() > 0) parts.add(spec.showDc() ? "CD " + spec.dc() : "CD oculta");
        String mode = Texts.modeLabel(spec.mode());
        if (!mode.isEmpty()) parts.add(mode);
        if (spec.secret()) parts.add("secreto");
        if (!spec.label().isEmpty()) parts.add(spec.label());
        return String.join(" · ", parts);
    }

    /** O alvo responde (tecla, tela ou {@code /ficha pedido <id>}). Id vazio: o pedido mais novo. */
    public static void respond(ServerPlayer player, String requestId) {
        long now = System.currentTimeMillis();
        String id = requestId;
        if (id.isEmpty()) {
            List<GmRequests.Request> pending = REQUESTS.pendingFor(player.getUUID(), now);
            if (pending.isEmpty()) {
                player.sendSystemMessage(ChatCards.info("Nenhum pedido do mestre aguardando você."));
                return;
            }
            id = pending.get(0).id();
        }
        GmRequests.ClaimResult claim = REQUESTS.claim(id, player.getUUID(), now);
        switch (claim.claim()) {
            case OK -> {
                GmRequests.Request request = claim.request();
                GmRequests.Target target = claim.target();
                if (request == null || target == null) return;
                GmRequests.Spec s = request.spec();
                RollSpec spec = new RollSpec(s.kind(), s.key(), s.attribute(), s.label(), s.dc(), s.showDc(), s.secret(),
                        s.mode(), s.privileged() ? request.gm() : null);
                tellGm(request, player.getUUID(), "rolando", "", null);
                execute(player, spec, request, target.commandId());
            }
            case BUSY -> status(player, "rolar", "ocupado", "Sua resposta já está a caminho.");
            case ANSWERED -> {
                closeRequestCard(player, id);
                status(player, "rolar", "erro", "Você já respondeu este pedido.");
            }
            case NOT_TARGET -> player.sendSystemMessage(ChatCards.error("Este pedido não é para você."));
            case NOT_FOUND -> {
                closeRequestCard(player, id);
                status(player, "rolar", "erro", "Este pedido não existe mais: venceu, foi cancelado ou o mestre saiu.");
            }
        }
    }

    public static void decline(ServerPlayer player, String requestId) {
        GmRequests.ClaimResult claim = REQUESTS.decline(requestId, player.getUUID(), System.currentTimeMillis());
        closeRequestCard(player, requestId);
        if (claim.claim() == GmRequests.Claim.OK && claim.request() != null) {
            tellGm(claim.request(), player.getUUID(), "recusou", "", null);
            if (!FichaNetwork.canOpen(player)) player.sendSystemMessage(ChatCards.info("Pedido recusado."));
        } else if (claim.claim() == GmRequests.Claim.NOT_FOUND && !FichaNetwork.canOpen(player)) {
            player.sendSystemMessage(ChatCards.info("Este pedido não existe mais."));
        }
    }

    private static void closeRequestCard(ServerPlayer player, String requestId) {
        closeRequestCard(player, requestId, "", "");
    }

    /**
     * Tira o cartão do HUD. {@code reason} não vazio ("foi cancelado pelo mestre", "terminou: o mestre
     * saiu do servidor") vira um aviso curto no cliente; cliente antigo só fecha o cartão.
     */
    private static void closeRequestCard(ServerPlayer player, String requestId, String test, String reason) {
        FichaNetwork.send(player, new FichaPayloads.Request(clip(requestId, FichaPayloads.REQUEST_ID), "", "",
                clip(test, FichaPayloads.LABEL), clip(reason, FichaPayloads.DETAIL), 0, false));
    }

    /** Uma resposta chegando à mesa do mestre que pediu (o cartão de rolagem ele já recebeu). */
    private static void tellGm(GmRequests.Request request, UUID target, String state, String detail, @Nullable RollCard card) {
        ServerPlayer gm = online(request.gm());
        GmRequests.Target t = request.target(target);
        if (gm == null || t == null) return;
        String test = request.spec().test();
        if (FichaNetwork.canOpen(gm)) {
            FichaNetwork.send(gm, new FichaPayloads.TableUpdate(request.id(), clip(test, FichaPayloads.LABEL), target,
                    clip(t.name(), FichaPayloads.NAME), state, clip(detail, FichaPayloads.MESSAGE),
                    card == null ? "" : clip(Json.write(card.toJson()), FichaPayloads.CARD_JSON)));
        } else if (!"rolando".equals(state) && !"rolou".equals(state) && !"cancelado".equals(state)) {
            // Sem a mesa: a rolagem ele já vê pela linha do cartão; "rolando" seria ruído; o
            // cancelamento foi ele quem fez (ou ele saiu). O resto vira uma linha curta.
            gm.sendSystemMessage(ChatCards.answerLine(t.name(), test, state, detail));
        }
    }

    // ── Ficha de outro jogador (mestre) ─────────────────────────────────────────

    public static void peek(ServerPlayer gm, UUID targetId) {
        if (!acceptRequest(gm, "espiar")) {
            status(gm, "mestre", "ocupado", "Uma ficha de cada vez: espere um instante.");
            return;
        }
        if (!isGm(gm)) {
            status(gm, "mestre", "erro", notGmMessage(gm));
            return;
        }
        Optional<SiteApi> site = FactBridge.site();
        if (site.isEmpty()) {
            status(gm, "mestre", "erro", INTEGRATION_OFF);
            return;
        }
        ServerPlayer target = gm.server.getPlayerList().getPlayer(targetId);
        if (target == null) {
            status(gm, "mestre", "erro", "Esse jogador não está online.");
            return;
        }
        JsonObject body = new JsonObject();
        body.addProperty("gm_profile_uuid", gm.getUUID().toString());
        body.addProperty("gm_account_uuid", accountOf(gm.server, gm.getUUID()).toString());
        body.addProperty("target_profile_uuid", targetId.toString());
        body.addProperty("target_account_uuid", accountOf(gm.server, targetId).toString());
        GameFacts.Subject subject = subject(target);
        if (subject.character() != null) body.addProperty("target_character_id", subject.character().toString());
        if (!subject.characterName().isEmpty()) body.addProperty("target_character_name", subject.characterName());
        UUID gmId = gm.getUUID();
        Object session = gm.connection;
        String targetName = displayName(target);
        call(site.get(), "/rpg/peek", body, 0, response -> {
            ServerPlayer online = back(gmId, session);
            if (online == null) return;
            if (response.transportFailure()) {
                failure(online, "mestre", response);
                return;
            }
            SheetData data = sheetData(response);
            String code = data.code();
            String message = peekMessage(code, targetName);
            if (FichaNetwork.canOpen(online)) {
                JsonObject extra = new JsonObject();
                extra.addProperty("peek_name", targetName);
                extra.addProperty("peek_id", targetId.toString());
                // As habilidades do alvo, lidas agora (saiu enquanto o site respondia: aba vazia).
                extra.add("habilidades", HabilidadesColeta.jsonFor(online.server.getPlayerList().getPlayer(targetId)));
                FichaNetwork.send(online, new FichaPayloads.View(FichaPayloads.OPEN | FichaPayloads.PEEK | FichaPayloads.GM, code,
                        viewJson(data, online, extra), clip(siteUrl(site.get(), data.sheet()), 300), clip(message, FichaPayloads.MESSAGE)));
            } else if ("ok".equals(code) && data.sheet() != null) {
                online.sendSystemMessage(ChatCards.text("Ficha de " + targetName + ":", ChatCards.GOLD));
                ChatCards.sheetSummary(data.sheet()).forEach(online::sendSystemMessage);
            } else {
                online.sendSystemMessage(ChatCards.info(message));
            }
        });
    }

    /** {@code /peek} fala do alvo, não de quem pergunta: "ele não vinculou", não "vincule a sua". */
    static String peekMessage(String code, String targetName) {
        return switch (code) {
            case "ok" -> "";
            case "gm_required" -> "O site não confirmou você como staff: a conta do site vinculada a este perfil precisa ter cargo"
                    + " de staff (confira o vínculo com /vincular).";
            case "not_linked" -> targetName + " ainda não vinculou a conta do site.";
            case "foreign" -> "O perfil de " + targetName + " e a conta dona dele estão vinculados a contas diferentes do site.";
            case "needs_binding" -> targetName + " está num alt que ainda não escolheu ficha.";
            case "no_sheet" -> targetName + " ainda não tem ficha ativa no site.";
            default -> codeMessage(code);
        };
    }

    // ── Auxiliares ──────────────────────────────────────────────────────────────

    /**
     * Mestre no jogo = permissão local do <b>perfil</b> ({@code permissaoMestre}). O alt de
     * {@code /personagem alt} nasce sem OP de propósito ("acabou o /deop para jogar"): quem joga no
     * alt é jogador, e a staff mestra pela conta principal. Por isso não há herança da permissão da
     * conta dona — um alt de staff veria rolagens secretas e o bônus dos NPCs dos outros. O site ainda
     * confirma a staff ({@code gm_*}) antes de aceitar vantagem, desvantagem e atributo alternativo.
     */
    public static boolean isGm(ServerPlayer player) {
        return player.hasPermissions(FichaConfig.get(FichaConfig.GM_PERMISSION));
    }

    private static boolean isAlt(ServerPlayer player) {
        return AltData.get(player.server).isAlt(player.getUUID());
    }

    /** Por que não é mestre, com o caminho: no alt, a resposta é "use a conta principal". */
    private static String notGmMessage(ServerPlayer player) {
        return isAlt(player)
                ? "No alt você joga como jogador: a mesa do mestre fica na sua conta principal (/personagem trocar)."
                : "A mesa do mestre é da staff deste servidor.";
    }

    /** Os ids dos pedidos do mestre esperando este jogador, do mais novo ao mais antigo (TAB do {@code /ficha pedido}). */
    public static List<String> pendingRequestIds(ServerPlayer player) {
        List<String> ids = new ArrayList<>();
        for (GmRequests.Request request : REQUESTS.pendingFor(player.getUUID(), System.currentTimeMillis())) ids.add(request.id());
        return ids;
    }

    /** Os ids dos pedidos ainda abertos deste mestre (TAB do {@code /ficha mestre cancelar}). */
    public static List<String> openRequestIds(ServerPlayer gm) {
        List<String> ids = new ArrayList<>();
        for (GmRequests.Request request : REQUESTS.openBy(gm.getUUID(), System.currentTimeMillis())) ids.add(request.id());
        return ids;
    }

    /** Os campos de identidade de toda chamada, os mesmos do diário (mais o nome do perfil). */
    private static JsonObject identity(ServerPlayer player) {
        GameFacts.Subject subject = subject(player);
        JsonObject body = new JsonObject();
        body.addProperty("profile_uuid", player.getUUID().toString());
        body.addProperty("account_uuid", accountOf(player.server, player.getUUID()).toString());
        body.addProperty("profile_name", player.getGameProfile().getName());
        if (subject.character() != null) body.addProperty("character_id", subject.character().toString());
        if (!subject.characterName().isEmpty()) body.addProperty("character_name", subject.characterName());
        return body;
    }

    /** {@code character_name} do backend tem teto de 160 runas; acima disso o site responde 400. */
    private static final int MAX_CHARACTER_NAME = 160;

    /** A conta dona do alt; a própria conta quando não é alt. */
    private static UUID accountOf(MinecraftServer server, UUID profile) {
        UUID owner = AltData.get(server).ownerOf(profile);
        return owner == null ? profile : owner;
    }

    private static GameFacts.Subject subject(ServerPlayer player) {
        GameFacts.Subject subject = GameFacts.subject(player.server, player.getUUID());
        // getName passa pelo mixin do essentials; getString retira os estilos sem aceitar
        // identidade enviada pelo cliente.
        String displayed = player.getName().getString().trim();
        String name = Json.clean(displayed.isEmpty() ? subject.characterName() : displayed, MAX_CHARACTER_NAME);
        return new GameFacts.Subject(subject.profile(), subject.character(), name, subject.house());
    }

    private static String displayName(ServerPlayer player) {
        String name = player.getName().getString().trim();
        return name.isEmpty() ? player.getGameProfile().getName() : name;
    }

    @Nullable
    private static String characterKey(ServerPlayer player) {
        UUID character = GameFacts.subject(player.server, player.getUUID()).character();
        return character == null ? null : character.toString();
    }

    private static SheetData sheetData(SiteApi.Response response) {
        return new SheetData(codeOf(response), FichaSheet.parse(response.object("sheet")), Binding.parse(response.object("binding")),
                Candidate.parseList(arrayOf(response, "candidates")));
    }

    /**
     * O código da resposta. O código vai num campo de 24 caracteres do pacote: código maior (backend
     * novo) não derruba ninguém. Resposta sem {@code code} não é "site fora" por padrão: 401/403 é a
     * credencial da integração, 404 é backend sem as rotas da ficha — os dois são da staff resolver, e
     * o jogador lê isso em vez de "tente de novo em instantes".
     */
    static String codeOf(SiteApi.Response response) {
        String code = response.code();
        // O backend nomeia os erros de configuração: token errado e integração desligada lá.
        String mapped = switch (code) {
            case "invalid_game_token" -> "auth";
            case "integration_disabled" -> "integration_disabled";
            case "" -> switch (response.status()) {
                case 400 -> "invalid_request";
                case 401, 403 -> "auth";
                case 404 -> "missing";
                default -> "unavailable";
            };
            default -> null;
        };
        if (mapped == null) return clip(code, 24);
        if (("auth".equals(mapped) || "missing".equals(mapped) || "integration_disabled".equals(mapped)) && !setupWarned) {
            setupWarned = true;
            AurorionFicha.LOGGER.warn("Ficha: o site respondeu HTTP {} às rotas /rpg/* — confira o token da integração e se o backend"
                    + " já tem as rotas da ficha no jogo", response.status());
        }
        return mapped;
    }

    /** Códigos que descrevem a ficha do perfil (e por isso entram no cache); o resto é erro. */
    private static boolean cacheable(String code) {
        return "ok".equals(code) || "needs_binding".equals(code) || "no_sheet".equals(code);
    }

    @Nullable
    private static JsonArray arrayOf(SiteApi.Response response, String member) {
        return Json.array(response.body(), member);
    }

    private static void remember(UUID profile, @Nullable String characterId, SheetData data) {
        if (data.sheet() != null) SUBJECT_TYPE.put(profile, data.sheet().subjectType());
        else SUBJECT_TYPE.remove(profile); // sem ficha agora: volta à regra do alt (isNpc)
        if (cacheable(data.code())) CACHE.put(profile, characterId, data);
        MinecraftServer current = server;
        if (current != null) SheetStatsStore.record(current, profile, data.code(), data.sheet());
    }

    /** O JSON da tela: {@code {sheet, binding, candidates, profile_name, ...extra}}, dentro do teto do pacote. */
    private static String viewJson(SheetData data, ServerPlayer player, @Nullable JsonObject extra) {
        JsonObject json = new JsonObject();
        json.add("sheet", data.sheet() == null ? JsonNull.INSTANCE : data.sheet().toJson());
        json.add("binding", data.binding() == null ? JsonNull.INSTANCE : data.binding().toJson());
        json.add("candidates", Candidate.toJson(data.candidates()));
        json.addProperty("profile_name", player.getGameProfile().getName());
        // Toda View leva as habilidades no jogo (a tela troca a aba inteira a cada View; [] quando não há).
        // Espiar manda as do alvo em extra, e não as do mestre.
        if (extra == null || !extra.has("habilidades")) json.add("habilidades", HabilidadesColeta.json(player));
        if (extra != null) for (var e : extra.entrySet()) json.add(e.getKey(), e.getValue());
        return fit(json, FichaPayloads.VIEW_JSON);
    }

    /**
     * Cabe no teto do pacote? Os modelos já limitam cada campo; se ainda assim passar, a lista de
     * candidatos (o que mais cresce) encolhe pela metade até caber, e depois os grupos de habilidades
     * saem do último para o primeiro — a ficha nunca é cortada.
     */
    static String fit(JsonObject json, int max) {
        String text = Json.write(json);
        while (text.length() > max && json.get("candidates") instanceof JsonArray list && !list.isEmpty()) {
            JsonArray half = new JsonArray();
            for (int i = 0; i < list.size() / 2; i++) half.add(list.get(i));
            json.add("candidates", half);
            text = Json.write(json);
        }
        // Depois as habilidades: sai o último grupo (o de menor prioridade) até caber.
        while (text.length() > max && json.get("habilidades") instanceof JsonArray groups && !groups.isEmpty()) {
            groups.remove(groups.size() - 1);
            text = Json.write(json);
        }
        if (text.length() > max) {
            AurorionFicha.LOGGER.warn("Ficha: resposta grande demais para o pacote ({} caracteres); enviada vazia", text.length());
            return "{}";
        }
        return text;
    }

    private static String siteUrl(SiteApi site, @Nullable FichaSheet sheet) {
        java.net.URI base = site.base();
        String path = sheet != null && sheet.sitePath().startsWith("/") ? sheet.sitePath() : "/profile/rpg";
        return base.getScheme() + "://" + base.getHost() + (base.getPort() > 0 ? ":" + base.getPort() : "") + path;
    }

    /** A explicação que a tela (ou o chat) mostra para cada código do site. */
    static String codeMessage(String code) {
        return switch (code) {
            case "ok" -> "";
            case "not_linked" -> FichaConfig.get(FichaConfig.NOT_LINKED);
            case "needs_binding" -> "Este personagem ainda não tem ficha. Escolha qual ficha ou NPC ele usa.";
            case "no_sheet" -> "Você ainda não tem uma ficha ativa no site. Crie ou ative uma em Perfil → RPG.";
            case "foreign" -> "Este perfil Minecraft está vinculado a outra conta do site. Se isso estiver errado, chame a staff.";
            case "gm_required" -> "Isso exige um mestre confirmado pelo site.";
            case "forbidden" -> "Sem permissão para esta ficha.";
            case "not_found" -> "Essa ficha não existe mais no site.";
            // 400 do site: o pedido saiu malformado daqui (bug do mod ou versão diferente do backend).
            case "invalid_request" -> "O site não entendeu o pedido. Avise a staff (versão do mod e do site).";
            case "auth" -> "O servidor do jogo não conseguiu se identificar no site (credencial da integração). Avise a staff.";
            case "missing" -> "O site ainda não tem a ficha do jogo ativada. Avise a staff.";
            case "integration_disabled" -> "A integração com o jogo está desligada no site. Avise a staff.";
            default -> FichaConfig.get(FichaConfig.SITE_DOWN);
        };
    }

    /** {@code /roll} erra com {@code {code, message}}; a mensagem do site entra quando ajuda. */
    static String rollMessage(String code, SiteApi.Response response) {
        String detail = siteDetail(response);
        return switch (code) {
            case "not_linked", "needs_binding", "no_sheet", "foreign", "invalid_request", "auth", "missing" -> codeMessage(code);
            // no_approved_build ou resources_need_review: o site diz qual ("recursos de X aguardam revisão da staff").
            case "no_build" -> detail.isEmpty()
                    ? "Esta ficha ainda não tem a construção nova aprovada. Se ainda não atualizou, monte-a no site (Perfil → RPG) e envie para aprovar; a rolagem livre já funciona."
                    : "Esta ficha ainda não pode rolar: " + detail + ".";
            // idempotency_key_reused: o mesmo pedido do mestre respondido de novo com outra ficha no perfil.
            case "invalid" -> detail.contains("command_id")
                    ? "Este pedido já foi respondido com outra ficha. Peça ao mestre um pedido novo."
                    : "O site recusou este teste" + (detail.isEmpty() ? "." : ": " + detail + ".");
            case "gm_required" -> "Vantagem, desvantagem e atributo alternativo exigem um mestre confirmado pelo site.";
            case "forbidden" -> "O site não deixou esta ficha rolar" + (detail.isEmpty() ? "." : ": " + detail + ".");
            case "busy" -> {
                String reason = busyReason(response);
                yield "O site segurou esta rolagem: " + reason + (reason.contains("tente de novo") ? "." : ". Tente de novo em instantes.");
            }
            default -> FichaConfig.get(FichaConfig.SITE_DOWN);
        };
    }

    /**
     * {@code busy} do {@code /roll} não é "outra rolagem a caminho" (isso o servidor do jogo já barra
     * em {@code ROLLING}): é o limite de rolagens por dono no site ou o personagem preso num encontro
     * de mesa do site ({@code character_in_other_encounter}).
     */
    static String busyReason(SiteApi.Response response) {
        String detail = siteDetail(response);
        return detail.isEmpty() ? "este personagem está num encontro em andamento no site" : detail;
    }

    /** O {@code message} do site legível para o jogador ({@link Texts#siteDetail}). */
    static String siteDetail(SiteApi.Response response) {
        return Texts.siteDetail(Json.str(response.body(), "message", 160));
    }

    private static void failure(ServerPlayer player, String context, SiteApi.Response response) {
        String message = FichaConfig.get(FichaConfig.SITE_DOWN);
        if (FichaNetwork.canOpen(player)) status(player, context, "erro", message);
        else player.sendSystemMessage(ChatCards.error(message));
        AurorionFicha.LOGGER.debug("Ficha: site sem resposta em {} (HTTP {})", context, response.status());
    }

    private static void status(ServerPlayer player, String context, String state, String message) {
        if (FichaNetwork.canOpen(player)) FichaNetwork.send(player, new FichaPayloads.Status(context, state, clip(message, FichaPayloads.MESSAGE)));
        else if (!"ok".equals(state)) player.sendSystemMessage(ChatCards.error(message));
    }

    /**
     * Chama o site e entrega a resposta <b>na thread do servidor</b> que fez a chamada. Se esse
     * servidor parou (ou, no singleplayer, outro mundo abriu) antes da resposta, ela é descartada:
     * jogador, mapas e pedidos daqui já foram limpos em {@link #stop} e não são os do servidor novo.
     */
    private static void call(SiteApi site, String path, JsonObject body, int retries, Consumer<SiteApi.Response> onServer) {
        MinecraftServer origin = server;
        if (origin == null) return;
        // exceptionally: qualquer erro inesperado no caminho vira "sem resposta", para a trava da
        // rolagem (ROLLING) e as marcas de "a caminho" sempre serem soltas pelo callback.
        post(site, path, body, retries).exceptionally(error -> {
            AurorionFicha.LOGGER.warn("Ficha: erro inesperado chamando {}", path, error);
            return SiteApi.Response.transport();
        }).thenAccept(response -> {
            if (server != origin) return;
            origin.execute(() -> {
                if (server != origin) return;
                try {
                    onServer.accept(response);
                } catch (RuntimeException e) {
                    AurorionFicha.LOGGER.error("Ficha: erro tratando a resposta de {}", path, e);
                }
            });
        });
    }

    /**
     * Roda {@code onServer} na thread do servidor depois de {@code delayMs}, sem relógio por tick (o
     * atraso é do {@code delayedExecutor}). Servidor que parou nesse meio-tempo descarta.
     */
    private static void later(long delayMs, Runnable onServer) {
        MinecraftServer origin = server;
        if (origin == null) return;
        CompletableFuture.delayedExecutor(delayMs, TimeUnit.MILLISECONDS).execute(() -> {
            if (server != origin) return;
            origin.execute(() -> {
                if (server == origin) onServer.run();
            });
        });
    }

    /** POST com contagem para o diagnóstico; {@code retries} &gt; 0 só em chamada idempotente. */
    private static CompletableFuture<SiteApi.Response> post(SiteApi site, String path, JsonObject body, int retries) {
        CALLS.incrementAndGet();
        return site.post(path, body).thenCompose(response -> {
            lastResult = path + " → " + (response.status() == 0 ? "sem resposta"
                    : response.code().isEmpty() ? "HTTP " + response.status() : response.code());
            if (!response.transportFailure()) return CompletableFuture.completedFuture(response);
            FAILURES.incrementAndGet();
            if (retries <= 0) return CompletableFuture.completedFuture(response);
            Executor later = CompletableFuture.delayedExecutor(RETRY_DELAY_MS, TimeUnit.MILLISECONDS);
            return CompletableFuture.supplyAsync(() -> site, later).thenCompose(same -> post(same, path, body, retries - 1));
        });
    }

    /**
     * Um pedido de cada tipo a cada 250 ms por jogador. Por tipo, e não geral: a tela pede a lista
     * de candidatos logo depois de abrir, e um limite único descartaria esse segundo pedido.
     */
    private static boolean acceptRequest(ServerPlayer player, String action) {
        return acceptRequest(player, action, MIN_REQUEST_INTERVAL_MS);
    }

    private static boolean acceptRequest(ServerPlayer player, String action, long intervalMs) {
        long now = System.currentTimeMillis();
        Map<String, Long> mine = LAST_REQUEST.computeIfAbsent(player.getUUID(), id -> new HashMap<>(8));
        Long last = mine.get(action);
        if (last != null && now - last < intervalMs) return false;
        mine.put(action, now);
        return true;
    }

    @Nullable
    private static ServerPlayer online(UUID id) {
        MinecraftServer current = server;
        return current == null ? null : current.getPlayerList().getPlayer(id);
    }

    /**
     * O jogador, se ainda for a mesma sessão que fez a chamada. A conexão (e não o objeto do
     * jogador) é a identidade da sessão: renascer cria outro {@code ServerPlayer} na mesma conexão;
     * sair e voltar — ou trocar de alt e voltar — cria outra conexão.
     */
    @Nullable
    private static ServerPlayer back(UUID id, Object session) {
        ServerPlayer player = online(id);
        return player != null && player.connection == session ? player : null;
    }

    private static String clip(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }

    private static long parseLong(String text) {
        try {
            return Long.parseLong(text.trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    @Nullable
    private static UUID parseUuid(String text) {
        try {
            return UUID.fromString(text.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
