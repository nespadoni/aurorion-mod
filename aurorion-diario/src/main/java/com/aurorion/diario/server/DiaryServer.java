package com.aurorion.diario.server;

import com.aurorion.core.integration.GameFacts;
import com.aurorion.core.diagnostics.ServerDiagnostics;
import com.aurorion.core.character.AltData;
import com.aurorion.diario.AurorionDiario;
import com.aurorion.core.rate.ActionCooldown;
import com.aurorion.diario.markup.DiaryMarkup;
import com.aurorion.diario.markup.DiaryText;
import com.aurorion.diario.network.DiaryNetwork;
import com.aurorion.diario.network.DiaryPayloads;
import com.aurorion.integracao.bridge.FactBridge;
import com.aurorion.integracao.outbox.SiteApi;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * O lado do servidor do {@code /diario}: conversa com o site pelo {@link SiteApi} da integração e
 * com a tela do jogador pelos pacotes do diário.
 *
 * <h2>Custo</h2>
 * Nada roda por tick. Cada ação do jogador vira uma chamada assíncrona ao site; a resposta volta à
 * thread do servidor com {@code server.execute} antes de tocar no jogador. A única rotina periódica
 * é o reenvio do que ficou guardado com o site fora do ar, numa thread própria, a cada 30 s — e ela
 * só faz trabalho quando há algo guardado. A gravação em disco tem outra thread: esperar o site
 * nunca atrasa a confirmação de que o texto ficou guardado.
 *
 * <h2>Quem é quem</h2>
 * O perfil e o personagem vêm sempre da sessão do jogador no servidor ({@link GameFacts#subject});
 * a tela nunca diz de quem é o diário. O site ainda confere o vínculo do perfil a cada chamada. As
 * sessões de edição ({@code draftKey}) são escolhidas pelo cliente, então todo mapa daqui as guarda
 * sob o perfil de quem as usou ({@link PendingStore#key}).
 */
@EventBusSubscriber(modid = AurorionDiario.MOD_ID)
public final class DiaryServer {
    /**
     * Ritmo por perfil (ver {@link RequestPacer}): listar e abrir uma entrada, uma vez a cada 2 s
     * cada; gravar, uma vez a cada 3 s. A tela salva 2 s depois da última tecla e espera a resposta
     * antes de mandar de novo, então o normal é a gravação esperar um pouco a vez — nunca sumir.
     */
    private static final long LIST_INTERVAL_MS = 2_000L;
    private static final long GET_INTERVAL_MS = 2_000L;
    private static final long PUBLISH_INTERVAL_MS = 2_000L;
    private static final long SAVE_INTERVAL_MS = 3_000L;
    /** Sessões de edição de um perfil esperando a vez de gravar. Passou disso, a tela ouve "lento". */
    private static final int SAVE_SLOTS = 4;
    private static final long DISCARD_INTERVAL_MS = 250L;
    private static final long RETRY_EVERY_S = 30L;
    private static final int RETRY_BATCH = 10;
    /** Espera máxima por uma resposta no reenvio: a thread do diário nunca fica presa para sempre. */
    private static final long RETRY_WAIT_S = 30L;
    private static final int MAX_EDIT_SESSIONS = 32;

    private static final RequestPacer LIST = pacer(LIST_INTERVAL_MS, 1);
    private static final RequestPacer GET = pacer(GET_INTERVAL_MS, 1);
    private static final RequestPacer PUBLISH = pacer(PUBLISH_INTERVAL_MS, 2);
    private static final RequestPacer SAVE = pacer(SAVE_INTERVAL_MS, SAVE_SLOTS);
    /** Descartar só mexe no disco local: o teto curto de antes basta, e não zera no logout. */
    private static final ActionCooldown DISCARD = new ActionCooldown(DISCARD_INTERVAL_MS);
    /** Sessão de edição → entrada criada nela, para as gravações seguintes não criarem outra. */
    private static final Map<String, Long> CREATED = new ConcurrentHashMap<>();
    /** Sessão de edição → última operação enviada; resposta de uma operação velha não guarda nada. */
    private static final Map<String, String> LATEST_OP = new ConcurrentHashMap<>();
    private static final AtomicBoolean RETRY_KICKED = new AtomicBoolean();

    @Nullable
    private static volatile MinecraftServer server;
    @Nullable
    private static volatile PendingStore store;
    @Nullable
    private static volatile ScheduledExecutorService worker;
    @Nullable
    private static volatile ExecutorService disk;

    private DiaryServer() {
    }

    // ── Ciclo de vida ───────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onStarted(ServerStartedEvent event) {
        stop();
        server = event.getServer();
        worker = Executors.newSingleThreadScheduledExecutor(daemon("aurorion-diario"));
        ExecutorService io = Executors.newSingleThreadExecutor(daemon("aurorion-diario-disco"));
        disk = io;
        store = new PendingStore(server.getWorldPath(LevelResource.ROOT).resolve(AurorionDiario.MOD_ID).resolve("pendentes.json"),
                io::execute, AurorionDiario.LOGGER::warn);
        ServerDiagnostics.register("Diário", DiaryServer::diagnostics);
        worker.scheduleWithFixedDelay(DiaryServer::retryPending, RETRY_EVERY_S, RETRY_EVERY_S, TimeUnit.SECONDS);
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent event) {
        stop();
    }

    /**
     * Sair não zera o ritmo de ninguém (reconectar não compra chamadas novas). A gravação que esperava
     * a vez sai agora: o jogador ainda está na lista durante este evento, e o texto não pode se perder
     * porque a pessoa fechou o jogo. Listar e abrir que esperavam não têm mais para quem responder.
     */
    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        SAVE.flush(id.toString());
        String prefix = PendingStore.key(id.toString(), "");
        CREATED.keySet().removeIf(key -> key.startsWith(prefix));
        LATEST_OP.keySet().removeIf(key -> key.startsWith(prefix));
    }

    private static void stop() {
        ServerDiagnostics.unregister("Diário");
        if (worker != null) worker.shutdownNow(); // o reenvio pode parar no meio: o recibo do site cobre a repetição
        if (disk != null) {
            disk.shutdown(); // a thread é daemon: espera a última gravação em disco antes de o processo sair
            try {
                if (!disk.awaitTermination(5, TimeUnit.SECONDS)) AurorionDiario.LOGGER.warn("Diario: gravação dos rascunhos guardados não terminou a tempo");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        worker = null;
        disk = null;
        store = null;
        server = null;
        CREATED.clear();
        LATEST_OP.clear();
        LIST.clear();
        GET.clear();
        PUBLISH.clear();
        SAVE.clear();
        DISCARD.clear();
        RETRY_KICKED.set(false);
    }

    private static RequestPacer pacer(long intervalMillis, int slots) {
        return new RequestPacer(intervalMillis, slots, System::currentTimeMillis, (task, delay) -> {
            ScheduledExecutorService current = worker;
            if (current == null) return false;
            try {
                current.schedule(() -> runOnServer(task), delay, TimeUnit.MILLISECONDS);
                return true;
            } catch (RejectedExecutionException e) {
                return false;
            }
        });
    }

    /** Roda {@code action} com o jogador ainda online; quem saiu enquanto esperava a vez fica sem. */
    private static Runnable whenOnline(UUID id, java.util.function.Consumer<ServerPlayer> action) {
        return () -> {
            ServerPlayer online = online(id);
            if (online != null) action.accept(online);
        };
    }

    private static List<String> diagnostics() {
        PendingStore current = store;
        if (current == null) return List.of("Estado: parado");
        var stats = current.diagnostics();
        var lines = new ArrayList<String>();
        lines.add("Rascunhos pendentes: " + stats.total());
        lines.add("Aguardando reenvio: " + stats.retryable());
        lines.add("Conflitos/recusas que precisam de revisão: " + stats.conflicts());
        lines.add("Arquivo de rascunhos legível: " + (stats.readable() ? "sim" : "não; conferir log"));
        lines.add("Gravação em andamento: " + (stats.writing() ? "sim" : "não")
                + "; confirmações aguardando disco: " + stats.waitingForDisk());
        lines.add("Última gravação: " + (stats.lastWriteAt() == 0 ? "nenhuma"
                : stats.lastWriteSucceeded() ? "ok" : "falhou; conferir log"));
        if (stats.oldestSavedAt() > 0) {
            long minutes = Math.max(0, System.currentTimeMillis() - stats.oldestSavedAt()) / 60_000;
            lines.add("Rascunho pendente mais antigo: " + minutes + " minuto(s)");
        }
        lines.add("Transporte do site disponível: " + (FactBridge.site().isPresent() ? "sim" : "não"));
        lines.add("Reenvio periódico: a cada " + RETRY_EVERY_S + " s, até " + RETRY_BATCH + " por rodada");
        return lines;
    }

    private static java.util.concurrent.ThreadFactory daemon(String name) {
        return runnable -> {
            Thread thread = new Thread(runnable, name);
            thread.setDaemon(true);
            return thread;
        };
    }

    // ── /diario ─────────────────────────────────────────────────────────────────

    public static void open(ServerPlayer player) {
        UUID id = player.getUUID();
        LIST.submit(id.toString(), "lista", whenOnline(id, DiaryServer::list));
    }

    private static void list(ServerPlayer player) {
        Optional<SiteApi> site = FactBridge.site();
        if (site.isEmpty()) {
            player.sendSystemMessage(error("O diário depende da integração com o site, que está desligada neste servidor."));
            return;
        }
        if (!DiaryNetwork.canOpen(player)) {
            player.sendSystemMessage(siteLink(site.get(), "Seu cliente não tem a tela do diário. Escreva pelo site: "));
            return;
        }
        GameFacts.Subject subject = diarySubject(player);
        if (subject.character() == null) {
            player.sendSystemMessage(error("Crie seu personagem antes de abrir o diário."));
            return;
        }
        UUID id = player.getUUID();
        site.get().post("/diary/list", actor(player, subject)).thenAccept(response -> runOnServer(() -> {
            ServerPlayer online = online(id);
            if (online == null) return;
            switch (response.code()) {
                case "ok" -> DiaryNetwork.send(online, new DiaryPayloads.Open(
                        DiaryText.clip(subject.characterName().isEmpty() ? "seu personagem" : subject.characterName(), DiaryPayloads.CHARACTER),
                        summaries(response.body() != null ? response.body().get("entries") : null, id.toString()),
                        siteUrl(site.get())));
                case "not_linked" -> online.sendSystemMessage(info("Vincule sua conta do site primeiro: no site, abra Perfil → Diário → "
                        + "\"Vincular minha conta Minecraft\" e digite aqui /vincular CÓDIGO."));
                default -> online.sendSystemMessage(error("O site não respondeu agora. Tente /diario de novo em instantes."));
            }
        }));
    }

    // ── Pacotes da tela ─────────────────────────────────────────────────────────

    public static void request(ServerPlayer player, DiaryPayloads.Request payload) {
        UUID id = player.getUUID();
        switch (payload.action()) {
            case "lista" -> open(player);
            // Um lugar só: clicar em outra entrada enquanto a anterior espera a vez troca o pedido.
            case "abrir" -> GET.submit(id.toString(), "abrir", whenOnline(id, online -> openEntry(online, payload.entryId())));
            case "descartar" -> {
                if (store != null && DISCARD.acquire(id, System.currentTimeMillis())) {
                    store.discardConflicts(id.toString(), payload.entryId());
                }
            }
            default -> { }
        }
    }

    private static void openEntry(ServerPlayer player, long entryId) {
        Optional<SiteApi> site = FactBridge.site();
        if (site.isEmpty() || entryId <= 0) return;
        String profile = player.getUUID().toString();
        GameFacts.Subject subject = diarySubject(player);
        JsonObject body = actor(player, subject);
        body.addProperty("entry_id", entryId);
        UUID id = player.getUUID();

        // Há uma cópia guardada aqui, mais nova que a do site: é ela que a pessoa continua editando.
        // Ela só some quando o site confirmar uma gravação desta sessão (ou a pessoa ficar com a do site).
        Optional<PendingStore.Pending> local = store != null ? store.forEntry(profile, entryId) : Optional.empty();
        if (local.isPresent()) {
            PendingStore.Pending pending = local.get();
            String markup = markupOf(documentOf(pending));
            DiaryNetwork.send(player, new DiaryPayloads.Entry(entryId, pending.baseVersion(),
                    DiaryText.clip(pending.title(), DiaryPayloads.TITLE_WIRE), DiaryText.clip(pending.loreDate(), DiaryPayloads.LORE_DATE_WIRE),
                    markup, DiaryPayloads.LOCAL | readOnly(markup), DiaryText.clip(pending.draftKey(), DiaryPayloads.KEY)));
            if (!pending.conflict()) {
                kickRetry();
                return;
            }
            // O site recusou a cópia por conflito: mostra a versão de lá para a pessoa escolher.
            site.get().post("/diary/get", body).thenAccept(response -> runOnServer(() -> {
                ServerPlayer online = online(id);
                JsonObject entry = response.object("entry");
                if (online != null && "ok".equals(response.code()) && entry != null) sendConflict(online, pending.draftKey(), entry);
            }));
            return;
        }
        site.get().post("/diary/get", body).thenAccept(response -> runOnServer(() -> {
            ServerPlayer online = online(id);
            if (online == null) return;
            JsonObject entry = response.object("entry");
            if (!"ok".equals(response.code()) || entry == null) {
                DiaryNetwork.send(online, status("", "erro", entryId, 0, 0, message(response)));
                return;
            }
            String markup = markupOf(entry.get("document") instanceof JsonObject doc ? doc : new JsonObject());
            DiaryNetwork.send(online, new DiaryPayloads.Entry(entryId, intOf(entry, "version"),
                    DiaryText.clip(stringOf(entry, "title"), DiaryPayloads.TITLE_WIRE),
                    DiaryText.clip(stringOf(entry, "lore_date"), DiaryPayloads.LORE_DATE_WIRE), markup, flagsOf(entry) | readOnly(markup), ""));
        }));
    }

    /**
     * Gravação vinda da tela. A chave da sessão e o id da operação vêm do cliente e vão para mapas,
     * arquivo e log: só passam no formato de UUID ({@link DiaryText#validKey}) — uma quebra de linha
     * ali forjaria linhas no log do servidor.
     */
    public static void save(ServerPlayer player, DiaryPayloads.Save payload) {
        if (!DiaryText.validKey(payload.draftKey()) || !DiaryText.validKey(payload.operationId())) return;
        UUID id = player.getUUID();
        // O balde é do perfil; o lugar é da sessão: duas entradas abertas em sequência nunca se
        // atropelam, e a versão nova de uma mesma sessão substitui a que esperava.
        RequestPacer.Result result = SAVE.submit(id.toString(), payload.draftKey(), whenOnline(id, online -> doSave(online, payload)));
        if (result == RequestPacer.Result.REFUSED) {
            DiaryNetwork.send(player, status(payload.draftKey(), "lento", payload.entryId(), payload.baseVersion(), 0, ""));
        }
    }

    private static void doSave(ServerPlayer player, DiaryPayloads.Save payload) {
        Optional<SiteApi> site = FactBridge.site();
        PendingStore pendingStore = store;
        if (site.isEmpty() || pendingStore == null) {
            DiaryNetwork.send(player, status(payload.draftKey(), "erro", payload.entryId(), 0, 0, "A integração com o site está desligada."));
            return;
        }
        long now = System.currentTimeMillis();
        if (DiaryText.isPlaceholder(payload.markup())) {
            // A tela recebeu o aviso no lugar de um texto longo demais: gravá-lo apagaria o texto no site.
            DiaryNetwork.send(player, status(payload.draftKey(), "erro", payload.entryId(), 0, 0,
                    "Esta entrada é longa demais para editar no jogo. Edite pelo site."));
            return;
        }

        GameFacts.Subject subject = diarySubject(player);
        if (subject.character() == null) {
            DiaryNetwork.send(player, status(payload.draftKey(), "erro", payload.entryId(), 0, 0, "Crie seu personagem antes de escrever."));
            return;
        }
        String profile = player.getUUID().toString();
        String session = PendingStore.key(profile, payload.draftKey());
        if (!LATEST_OP.containsKey(session)) {
            String prefix = PendingStore.key(profile, "");
            long sessions = LATEST_OP.keySet().stream().filter(key -> key.startsWith(prefix)).count();
            if (sessions >= MAX_EDIT_SESSIONS) {
                DiaryNetwork.send(player, status(payload.draftKey(), "erro", payload.entryId(), 0, 0,
                        "Muitas sessões de edição. Reconecte antes de criar outra entrada."));
                return;
            }
        }
        long entryId = payload.entryId() > 0 ? payload.entryId() : CREATED.getOrDefault(session, 0L);
        // O site conta caracteres Unicode; o pacote, unidades UTF-16. Corta pela conta do site.
        PendingStore.Pending pending = new PendingStore.Pending(payload.draftKey(), payload.operationId(), entryId, payload.baseVersion(),
                DiaryText.clipCodePoints(payload.title(), DiaryPayloads.TITLE), DiaryText.clipCodePoints(payload.loreDate(), DiaryPayloads.LORE_DATE),
                DiaryMarkup.toDocument(payload.markup()).toString(),
                profile, subject.character().toString(), subject.characterName(), now, false, accountProfile(player).toString());
        LATEST_OP.put(session, payload.operationId());

        // Com uma cópia desta sessão ainda na fila, a versão nova toma o lugar dela e espera a vez:
        // duas gravações da mesma sessão nunca viajam ao mesmo tempo. Cópia recusada por conflito
        // não está na fila; a gravação nova (a pessoa já escolheu) vai direto.
        Optional<PendingStore.Pending> stored = pendingStore.get(profile, payload.draftKey());
        if (stored.isPresent() && !stored.get().conflict()) {
            keepLocally(player.getUUID(), pending);
            return;
        }
        UUID id = player.getUUID();
        site.get().post("/diary/save", saveBody(pending)).thenAccept(response -> runOnServer(() -> onSaveResult(id, pending, response)));
    }

    private static void onSaveResult(UUID playerId, PendingStore.Pending pending, SiteApi.Response response) {
        ServerPlayer player = online(playerId);
        String session = PendingStore.key(pending.profile(), pending.draftKey());
        // Sem registro (o jogador saiu e o logout limpou a sessão) nada mais novo chegou: esta é a
        // última, e com o site fora ela precisa ficar guardada — é o caso da gravação feita no logout.
        String latestOp = LATEST_OP.get(session);
        boolean latest = latestOp == null || pending.operationId().equals(latestOp);
        JsonObject entry = response.object("entry");
        switch (response.code()) {
            case "ok" -> {
                if (entry == null) return;
                long entryId = longOf(entry, "id");
                int version = intOf(entry, "version");
                CREATED.put(session, entryId);
                if (store != null) store.acknowledge(pending, entryId, version);
                if (player != null) DiaryNetwork.send(player, status(pending.draftKey(), "site", entryId, version, flagsOf(entry), ""));
            }
            case "conflict" -> {
                if (player != null && entry != null) sendConflict(player, pending.draftKey(), entry);
            }
            default -> {
                if (response.transportFailure()) {
                    if (latest) keepLocally(playerId, pending);
                    return;
                }
                if (player != null) DiaryNetwork.send(player, status(pending.draftKey(), "erro", pending.entryId(), 0, 0, message(response)));
            }
        }
    }

    /** Guarda no disco do servidor e só então avisa a tela: "guardado" nunca é promessa vazia. */
    private static void keepLocally(UUID playerId, PendingStore.Pending pending) {
        PendingStore current = store;
        if (current == null) return;
        current.put(pending).thenAccept(kept -> runOnServer(() -> {
            ServerPlayer player = online(playerId);
            if (player == null) return;
            DiaryNetwork.send(player, kept
                    ? status(pending.draftKey(), "servidor", pending.entryId(), pending.baseVersion(), DiaryPayloads.LOCAL,
                    "O site está fora do ar. O texto ficou guardado no servidor do jogo e sincroniza sozinho.")
                    : status(pending.draftKey(), "erro", pending.entryId(), 0, 0,
                    "Não consegui guardar o texto no servidor do jogo. Copie seu texto antes de fechar a tela."));
        }));
    }

    public static void publish(ServerPlayer player, DiaryPayloads.Publish payload) {
        UUID id = player.getUUID();
        RequestPacer.Result result = PUBLISH.submit(id.toString(), Long.toString(payload.entryId()),
                whenOnline(id, online -> doPublish(online, payload)));
        if (result == RequestPacer.Result.REFUSED) {
            DiaryNetwork.send(player, status("", "erro", payload.entryId(), 0, 0, "Muitos pedidos seguidos. Tente de novo em instantes."));
        }
    }

    private static void doPublish(ServerPlayer player, DiaryPayloads.Publish payload) {
        Optional<SiteApi> site = FactBridge.site();
        if (site.isEmpty() || payload.entryId() <= 0) return;
        if (store != null && store.forEntry(player.getUUID().toString(), payload.entryId()).isPresent()) {
            DiaryNetwork.send(player, status("", "erro", payload.entryId(), 0, 0, "Espere o texto sincronizar com o site antes de publicar."));
            return;
        }
        GameFacts.Subject subject = diarySubject(player);
        JsonObject body = actor(player, subject);
        body.addProperty("entry_id", payload.entryId());
        body.addProperty("version", payload.version());
        UUID id = player.getUUID();
        String path = payload.publish() ? "/diary/publish" : "/diary/unpublish";
        site.get().post(path, body).thenAccept(response -> runOnServer(() -> {
            ServerPlayer online = online(id);
            if (online == null) return;
            JsonObject entry = response.object("entry");
            if ("ok".equals(response.code()) && entry != null) {
                DiaryNetwork.send(online, status("", payload.publish() ? "publicado" : "retirado", payload.entryId(),
                        intOf(entry, "version"), flagsOf(entry), payload.publish() && !Boolean.TRUE.equals(boolOf(entry, "profile_visible"))
                                ? "Publicado! Aparece no seu perfil público assim que a equipe aprovar o perfil." : ""));
            } else {
                DiaryNetwork.send(online, status("", "erro", payload.entryId(), 0, 0, message(response)));
            }
        }));
    }

    // ── Reenvio do que ficou guardado ────────────────────────────────────────────

    /** Adianta a próxima rodada de reenvio (no máximo uma na fila), sem esperar os 30 s. */
    private static void kickRetry() {
        ScheduledExecutorService current = worker;
        if (current == null || !RETRY_KICKED.compareAndSet(false, true)) return;
        try {
            current.execute(() -> {
                RETRY_KICKED.set(false);
                retryPending();
            });
        } catch (RejectedExecutionException e) {
            RETRY_KICKED.set(false);
        }
    }

    /**
     * Thread do diário. Bloqueia esperando o site de propósito: é a única coisa que ela faz. Mandar
     * de novo algo que o site já aplicou é seguro — o operation_id faz o site devolver o resultado.
     *
     * <p>A espera tem prazo e é interrompível: o {@code shutdownNow} do desligamento interrompe a
     * thread, e um {@code join()} sem prazo a deixaria presa até o site responder.
     */
    private static void retryPending() {
        PendingStore current = store;
        MinecraftServer owner = server;
        Optional<SiteApi> site = FactBridge.site();
        if (current == null || owner == null || site.isEmpty()) return;
        for (PendingStore.Pending pending : current.retryable(RETRY_BATCH)) {
            JsonObject body;
            try {
                body = saveBody(pending);
            } catch (RuntimeException e) {
                current.markConflict(pending, 0L); // documento guardado ilegível: não adianta reenviar
                AurorionDiario.LOGGER.warn("Diario: rascunho guardado com documento ilegivel, sessao {}", safeKey(pending.draftKey()));
                continue;
            }
            SiteApi.Response response;
            try {
                response = site.get().post("/diary/save", body).get(RETRY_WAIT_S, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (ExecutionException | TimeoutException e) {
                return; // tratado como site fora do ar: tenta na próxima rodada
            }
            if (response.transportFailure()) return; // o site continua fora: tenta na próxima rodada
            try {
                owner.execute(() -> onRetryResult(pending, response));
            } catch (RejectedExecutionException e) {
                return; // servidor desligando
            }
        }
    }

    private static void onRetryResult(PendingStore.Pending pending, SiteApi.Response response) {
        PendingStore current = store;
        if (current == null) return;
        ServerPlayer player = online(UUID.fromString(pending.profile()));
        JsonObject entry = response.object("entry");
        String title = pending.title().isBlank() ? "Sem título" : pending.title();
        switch (response.code()) {
            case "ok" -> {
                if (entry == null) return;
                long entryId = longOf(entry, "id");
                int version = intOf(entry, "version");
                CREATED.put(PendingStore.key(pending.profile(), pending.draftKey()), entryId);
                current.acknowledge(pending, entryId, version);
                if (player != null) {
                    DiaryNetwork.send(player, status(pending.draftKey(), "site", entryId, version, flagsOf(entry), ""));
                    player.sendSystemMessage(Component.literal("✔ \"" + title + "\" foi sincronizado com o site.").withStyle(ChatFormatting.GREEN));
                }
            }
            case "conflict" -> {
                current.markConflict(pending, entry != null ? longOf(entry, "id") : 0L);
                if (player != null) player.sendSystemMessage(info("\"" + title + "\" foi alterado no site enquanto ele estava fora. "
                        + "Abra a entrada no /diario para escolher qual versão fica."));
            }
            default -> {
                // Erro de regra (personagem, vínculo, documento): não adianta insistir. A cópia fica
                // guardada — no /diario, se a entrada existe; senão, no arquivo, para a equipe.
                current.markConflict(pending, 0L);
                AurorionDiario.LOGGER.warn("Diario: rascunho guardado recusado pelo site ({}), sessão {}",
                        safeKey(response.code()), safeKey(pending.draftKey()));
                if (player != null) player.sendSystemMessage(error("O site recusou \"" + title + "\": " + message(response)
                        + " O texto continua guardado no servidor do jogo."));
            }
        }
    }

    // ── Auxiliares ──────────────────────────────────────────────────────────────

    private static void sendConflict(ServerPlayer player, String draftKey, JsonObject entry) {
        String markup = markupOf(entry.get("document") instanceof JsonObject doc ? doc : new JsonObject());
        DiaryNetwork.send(player, new DiaryPayloads.Conflict(DiaryText.clip(draftKey, DiaryPayloads.KEY), longOf(entry, "id"), intOf(entry, "version"),
                DiaryText.clip(stringOf(entry, "title"), DiaryPayloads.TITLE_WIRE),
                DiaryText.clip(stringOf(entry, "lore_date"), DiaryPayloads.LORE_DATE_WIRE), markup,
                DiaryText.clip(stringOf(entry, "origin"), DiaryPayloads.ORIGIN)));
    }

    /**
     * A marcação que vai para a tela, já no teto do pacote: texto que não cabe vira o aviso
     * {@link DiaryText#TOO_LONG}, e a entrada abre só para leitura ({@link #readOnly}).
     */
    private static String markupOf(JsonObject document) {
        return DiaryMarkup.toMarkup(document, DiaryPayloads.MARKUP);
    }

    private static JsonObject documentOf(PendingStore.Pending pending) {
        try {
            JsonElement parsed = JsonParser.parseString(pending.document());
            return parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject();
        } catch (RuntimeException e) {
            return new JsonObject();
        }
    }

    /** Para o log: só o que tem formato de chave; o resto vira um marcador. */
    private static String safeKey(String key) {
        return DiaryText.validKey(key) ? key : "<invalida>";
    }

    private static JsonObject actor(ServerPlayer player, GameFacts.Subject subject) {
        JsonObject body = new JsonObject();
        body.addProperty("profile_uuid", player.getUUID().toString());
        body.addProperty("account_uuid", accountProfile(player).toString());
        if (subject.character() != null) body.addProperty("character_id", subject.character().toString());
        if (!subject.characterName().isEmpty()) body.addProperty("character_name", subject.characterName());
        return body;
    }

    private static JsonObject saveBody(PendingStore.Pending pending) {
        JsonObject body = new JsonObject();
        body.addProperty("profile_uuid", pending.profile());
        body.addProperty("account_uuid", pending.accountProfile() == null ? pending.profile() : pending.accountProfile());
        body.addProperty("character_id", pending.characterId());
        if (!pending.characterName().isEmpty()) body.addProperty("character_name", pending.characterName());
        long entryId = pending.entryId() > 0 ? pending.entryId()
                : CREATED.getOrDefault(PendingStore.key(pending.profile(), pending.draftKey()), 0L);
        body.addProperty("entry_id", entryId);
        body.addProperty("operation_id", pending.operationId());
        body.addProperty("base_version", pending.baseVersion());
        body.addProperty("title", pending.title());
        body.addProperty("lore_date", pending.loreDate());
        body.add("document", JsonParser.parseString(pending.document()));
        return body;
    }

    /** Identidade narrativa continua sendo a do perfil ativo; autoria usa a conta dona do alt. */
    private static UUID accountProfile(ServerPlayer player) {
        UUID owner = AltData.get(player.server).ownerOf(player.getUUID());
        return owner == null ? player.getUUID() : owner;
    }

    private static GameFacts.Subject diarySubject(ServerPlayer player) {
        GameFacts.Subject subject = GameFacts.subject(player.server, player.getUUID());
        // getName passa pelo mixin do essentials; getString retira os estilos sem aceitar
        // identidade enviada pelo cliente.
        String displayed = player.getName().getString().trim();
        String name = displayed.isEmpty() ? subject.characterName() : displayed;
        return new GameFacts.Subject(subject.profile(), subject.character(), name, subject.house());
    }

    private static List<DiaryPayloads.Summary> summaries(@Nullable JsonElement entries, String profile) {
        List<DiaryPayloads.Summary> out = new ArrayList<>();
        if (!(entries instanceof JsonArray array)) return out;
        for (JsonElement element : array) {
            if (!(element instanceof JsonObject entry)) continue;
            long id = longOf(entry, "id");
            int flags = flagsOf(entry);
            if (store != null && store.forEntry(profile, id).isPresent()) flags |= DiaryPayloads.LOCAL;
            out.add(new DiaryPayloads.Summary(id, DiaryText.clip(stringOf(entry, "title"), DiaryPayloads.TITLE_WIRE), flags));
            if (out.size() == DiaryPayloads.MAX_ENTRIES) break;
        }
        return out;
    }

    private static int flagsOf(JsonObject entry) {
        int flags = 0;
        if (Boolean.TRUE.equals(boolOf(entry, "published"))) flags |= DiaryPayloads.PUBLISHED;
        if (Boolean.TRUE.equals(boolOf(entry, "has_unpublished_changes"))) flags |= DiaryPayloads.CHANGES;
        if (Boolean.TRUE.equals(boolOf(entry, "hidden"))) flags |= DiaryPayloads.HIDDEN;
        return flags;
    }

    private static int readOnly(String markup) {
        return markup.length() > DiaryPayloads.MARKUP_EDIT || DiaryText.isPlaceholder(markup) ? DiaryPayloads.READ_ONLY : 0;
    }

    private static String message(SiteApi.Response response) {
        return switch (response.code()) {
            case "not_linked" -> "Vincule sua conta do site com /vincular antes de usar o diário.";
            case "wrong_character" -> "Esta entrada é de outro personagem. Edite-a pelo site.";
            case "not_found" -> "Esta entrada não existe mais.";
            case "stale" -> "Há uma versão mais nova salva. Reabra a entrada e publique de novo.";
            case "publication_changed" -> "A publicação desta entrada mudou no site. Reabra a entrada e tente de novo.";
            case "operation_reused" -> "O site recusou esta gravação repetida. Reabra a entrada e salve de novo.";
            case "empty" -> "Escreva um título ou algum texto antes de salvar.";
            case "limit" -> "Você chegou ao limite de entradas do diário.";
            case "attachment" -> "A entrada usa uma imagem que não está mais na sua biblioteca. Ajuste pelo site.";
            case "invalid_document" -> {
                String detail = response.body() != null ? DiaryText.clip(stringOf(response.body(), "message"), 200) : "";
                yield "O texto tem algo que o site não aceita" + (detail.isEmpty() ? "." : ": " + detail);
            }
            default -> "O site não respondeu agora. Tente de novo em instantes.";
        };
    }

    private static DiaryPayloads.Status status(String draftKey, String state, long entryId, int version, int flags, String message) {
        return new DiaryPayloads.Status(DiaryText.clip(draftKey, DiaryPayloads.KEY), state, entryId, version, flags,
                DiaryText.clip(message, DiaryPayloads.MESSAGE));
    }

    private static String siteUrl(SiteApi site) {
        java.net.URI base = site.base();
        return base.getScheme() + "://" + base.getHost() + (base.getPort() > 0 ? ":" + base.getPort() : "") + "/profile/diario";
    }

    private static Component siteLink(SiteApi site, String prefix) {
        String url = siteUrl(site);
        return Component.literal(prefix).withStyle(ChatFormatting.GRAY)
                .append(Component.literal(url).withStyle(style -> style.withColor(ChatFormatting.AQUA).withUnderlined(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url))));
    }

    @Nullable
    private static ServerPlayer online(UUID id) {
        return server == null ? null : server.getPlayerList().getPlayer(id);
    }

    private static void runOnServer(Runnable task) {
        MinecraftServer current = server;
        if (current != null) current.execute(task);
    }

    private static String stringOf(JsonObject object, String member) {
        JsonElement value = object.get(member);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : "";
    }

    /** Resposta do site é dado de fora: tipo errado vira 0, nunca exceção na thread do servidor. */
    private static int intOf(JsonObject object, String member) {
        long value = longOf(object, member);
        return value < Integer.MIN_VALUE || value > Integer.MAX_VALUE ? 0 : (int) value;
    }

    private static long longOf(JsonObject object, String member) {
        JsonElement value = object.get(member);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) return 0L;
        try {
            return value.getAsJsonPrimitive().getAsBigDecimal().longValueExact();
        } catch (NumberFormatException | ArithmeticException e) {
            return 0L;
        }
    }

    @Nullable
    private static Boolean boolOf(JsonObject object, String member) {
        JsonElement value = object.get(member);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean() ? value.getAsBoolean() : null;
    }

    private static Component info(String text) {
        return Component.literal(text).withStyle(ChatFormatting.GRAY);
    }

    private static Component error(String text) {
        return Component.literal(text).withStyle(ChatFormatting.RED);
    }
}
