package com.aurorion.diario.server;

import com.aurorion.core.integration.GameFacts;
import com.aurorion.diario.AurorionDiario;
import com.aurorion.diario.markup.DiaryMarkup;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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
    private static final long MIN_SAVE_INTERVAL_MS = 700L;
    private static final long RETRY_EVERY_S = 30L;
    private static final int RETRY_BATCH = 10;

    private static final Map<UUID, Long> LAST_SAVE = new HashMap<>();
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
        worker.scheduleWithFixedDelay(DiaryServer::retryPending, RETRY_EVERY_S, RETRY_EVERY_S, TimeUnit.SECONDS);
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent event) {
        stop();
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        LAST_SAVE.remove(id);
        String prefix = PendingStore.key(id.toString(), "");
        CREATED.keySet().removeIf(key -> key.startsWith(prefix));
        LATEST_OP.keySet().removeIf(key -> key.startsWith(prefix));
    }

    private static void stop() {
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
        LAST_SAVE.clear();
        RETRY_KICKED.set(false);
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
        Optional<SiteApi> site = FactBridge.site();
        if (site.isEmpty()) {
            player.sendSystemMessage(error("O diário depende da integração com o site, que está desligada neste servidor."));
            return;
        }
        if (!DiaryNetwork.canOpen(player)) {
            player.sendSystemMessage(siteLink(site.get(), "Seu cliente não tem a tela do diário. Escreva pelo site: "));
            return;
        }
        GameFacts.Subject subject = GameFacts.subject(player.server, player.getUUID());
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
                        subject.characterName().isEmpty() ? "seu personagem" : subject.characterName(),
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
        switch (payload.action()) {
            case "lista" -> open(player);
            case "abrir" -> openEntry(player, payload.entryId());
            case "descartar" -> {
                if (store != null) store.discardConflicts(player.getUUID().toString(), payload.entryId());
            }
            default -> { }
        }
    }

    private static void openEntry(ServerPlayer player, long entryId) {
        Optional<SiteApi> site = FactBridge.site();
        if (site.isEmpty() || entryId <= 0) return;
        String profile = player.getUUID().toString();
        GameFacts.Subject subject = GameFacts.subject(player.server, player.getUUID());
        JsonObject body = actor(player, subject);
        body.addProperty("entry_id", entryId);
        UUID id = player.getUUID();

        // Há uma cópia guardada aqui, mais nova que a do site: é ela que a pessoa continua editando.
        // Ela só some quando o site confirmar uma gravação desta sessão (ou a pessoa ficar com a do site).
        Optional<PendingStore.Pending> local = store != null ? store.forEntry(profile, entryId) : Optional.empty();
        if (local.isPresent()) {
            PendingStore.Pending pending = local.get();
            String markup = DiaryMarkup.toMarkup(JsonParser.parseString(pending.document()).getAsJsonObject());
            DiaryNetwork.send(player, new DiaryPayloads.Entry(entryId, pending.baseVersion(), pending.title(), pending.loreDate(),
                    markup, DiaryPayloads.LOCAL | readOnly(markup), pending.draftKey()));
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
            String markup = DiaryMarkup.toMarkup(entry.get("document") instanceof JsonObject doc ? doc : new JsonObject());
            DiaryNetwork.send(online, new DiaryPayloads.Entry(entryId, intOf(entry, "version"), stringOf(entry, "title"),
                    stringOf(entry, "lore_date"), markup, flagsOf(entry) | readOnly(markup), ""));
        }));
    }

    public static void save(ServerPlayer player, DiaryPayloads.Save payload) {
        Optional<SiteApi> site = FactBridge.site();
        PendingStore pendingStore = store;
        if (site.isEmpty() || pendingStore == null) {
            DiaryNetwork.send(player, status(payload.draftKey(), "erro", payload.entryId(), 0, 0, "A integração com o site está desligada."));
            return;
        }
        if (payload.draftKey().isBlank() || payload.operationId().isBlank()) return;
        long now = System.currentTimeMillis();
        Long last = LAST_SAVE.get(player.getUUID());
        if (last != null && now - last < MIN_SAVE_INTERVAL_MS) {
            DiaryNetwork.send(player, status(payload.draftKey(), "lento", payload.entryId(), payload.baseVersion(), 0, ""));
            return;
        }
        LAST_SAVE.put(player.getUUID(), now);

        GameFacts.Subject subject = GameFacts.subject(player.server, player.getUUID());
        if (subject.character() == null) {
            DiaryNetwork.send(player, status(payload.draftKey(), "erro", payload.entryId(), 0, 0, "Crie seu personagem antes de escrever."));
            return;
        }
        String profile = player.getUUID().toString();
        String session = PendingStore.key(profile, payload.draftKey());
        long entryId = payload.entryId() > 0 ? payload.entryId() : CREATED.getOrDefault(session, 0L);
        PendingStore.Pending pending = new PendingStore.Pending(payload.draftKey(), payload.operationId(), entryId, payload.baseVersion(),
                payload.title(), payload.loreDate(), DiaryMarkup.toDocument(payload.markup()).toString(),
                profile, subject.character().toString(), subject.characterName(), now, false);
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
        boolean latest = pending.operationId().equals(LATEST_OP.get(session));
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
        Optional<SiteApi> site = FactBridge.site();
        if (site.isEmpty() || payload.entryId() <= 0) return;
        if (store != null && store.forEntry(player.getUUID().toString(), payload.entryId()).isPresent()) {
            DiaryNetwork.send(player, status("", "erro", payload.entryId(), 0, 0, "Espere o texto sincronizar com o site antes de publicar."));
            return;
        }
        GameFacts.Subject subject = GameFacts.subject(player.server, player.getUUID());
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
     */
    private static void retryPending() {
        PendingStore current = store;
        MinecraftServer owner = server;
        Optional<SiteApi> site = FactBridge.site();
        if (current == null || owner == null || site.isEmpty()) return;
        for (PendingStore.Pending pending : current.retryable(RETRY_BATCH)) {
            SiteApi.Response response = site.get().post("/diary/save", saveBody(pending)).join();
            if (response.transportFailure()) return; // o site continua fora: tenta na próxima rodada
            owner.execute(() -> onRetryResult(pending, response));
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
                AurorionDiario.LOGGER.warn("Diario: rascunho guardado recusado pelo site ({}), sessão {}", response.code(), pending.draftKey());
                if (player != null) player.sendSystemMessage(error("O site recusou \"" + title + "\": " + message(response)
                        + " O texto continua guardado no servidor do jogo."));
            }
        }
    }

    // ── Auxiliares ──────────────────────────────────────────────────────────────

    private static void sendConflict(ServerPlayer player, String draftKey, JsonObject entry) {
        String markup = DiaryMarkup.toMarkup(entry.get("document") instanceof JsonObject doc ? doc : new JsonObject());
        DiaryNetwork.send(player, new DiaryPayloads.Conflict(draftKey, longOf(entry, "id"), intOf(entry, "version"),
                stringOf(entry, "title"), stringOf(entry, "lore_date"), markup, stringOf(entry, "origin")));
    }

    private static JsonObject actor(ServerPlayer player, GameFacts.Subject subject) {
        JsonObject body = new JsonObject();
        body.addProperty("profile_uuid", player.getUUID().toString());
        if (subject.character() != null) body.addProperty("character_id", subject.character().toString());
        if (!subject.characterName().isEmpty()) body.addProperty("character_name", subject.characterName());
        return body;
    }

    private static JsonObject saveBody(PendingStore.Pending pending) {
        JsonObject body = new JsonObject();
        body.addProperty("profile_uuid", pending.profile());
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

    private static List<DiaryPayloads.Summary> summaries(@Nullable JsonElement entries, String profile) {
        List<DiaryPayloads.Summary> out = new ArrayList<>();
        if (!(entries instanceof JsonArray array)) return out;
        for (JsonElement element : array) {
            if (!(element instanceof JsonObject entry)) continue;
            long id = longOf(entry, "id");
            int flags = flagsOf(entry);
            if (store != null && store.forEntry(profile, id).isPresent()) flags |= DiaryPayloads.LOCAL;
            out.add(new DiaryPayloads.Summary(id, stringOf(entry, "title"), flags));
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
        return markup.length() > DiaryPayloads.MARKUP_EDIT ? DiaryPayloads.READ_ONLY : 0;
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
                String detail = response.body() != null && response.body().has("message") ? response.body().get("message").getAsString() : "";
                yield "O texto tem algo que o site não aceita" + (detail.isEmpty() ? "." : ": " + detail);
            }
            default -> "O site não respondeu agora. Tente de novo em instantes.";
        };
    }

    private static DiaryPayloads.Status status(String draftKey, String state, long entryId, int version, int flags, String message) {
        String text = message.length() > DiaryPayloads.MESSAGE ? message.substring(0, DiaryPayloads.MESSAGE) : message;
        return new DiaryPayloads.Status(draftKey, state, entryId, version, flags, text);
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

    private static int intOf(JsonObject object, String member) {
        JsonElement value = object.get(member);
        return value != null && value.isJsonPrimitive() ? value.getAsInt() : 0;
    }

    private static long longOf(JsonObject object, String member) {
        JsonElement value = object.get(member);
        return value != null && value.isJsonPrimitive() ? value.getAsLong() : 0L;
    }

    @Nullable
    private static Boolean boolOf(JsonObject object, String member) {
        JsonElement value = object.get(member);
        return value != null && value.isJsonPrimitive() ? value.getAsBoolean() : null;
    }

    private static Component info(String text) {
        return Component.literal(text).withStyle(ChatFormatting.GRAY);
    }

    private static Component error(String text) {
        return Component.literal(text).withStyle(ChatFormatting.RED);
    }
}
