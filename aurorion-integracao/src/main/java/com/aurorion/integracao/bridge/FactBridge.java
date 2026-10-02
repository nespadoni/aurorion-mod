package com.aurorion.integracao.bridge;

import com.aurorion.core.integration.GameFacts;
import com.aurorion.core.diagnostics.ServerDiagnostics;
import com.aurorion.integracao.AurorionIntegracao;
import com.aurorion.integracao.config.IntegracaoConfig;
import com.aurorion.integracao.outbox.FactJson;
import com.aurorion.integracao.outbox.IngestClient;
import com.aurorion.integracao.outbox.Outbox;
import com.aurorion.integracao.outbox.SiteApi;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.ArrayList;
import java.util.List;

/**
 * Liga os fatos do jogo ({@link GameFacts}) a caixa de saida.
 *
 * <p>A caixa so existe com o servidor no ar e a integracao configurada. Fora disso,
 * {@link #accept} descarta em silencio e {@link #active()} responde {@code false} — quem monta um
 * fato (a morte, aqui; limbo, portais e casas, via {@code GameFacts.installed}) consulta isso antes
 * e nao paga nada.
 */
@EventBusSubscriber(modid = AurorionIntegracao.MOD_ID)
public final class FactBridge {
    private static final String SPOOL_DIR = AurorionIntegracao.MOD_ID;
    private static final String SPOOL_FILE = "pendentes.jsonl";

    @Nullable
    private static volatile Outbox outbox;
    @Nullable
    private static volatile SiteApi site;
    private static String state = "não iniciada";

    private FactBridge() {
    }

    /** Ha para onde enviar agora? */
    public static boolean active() {
        return outbox != null;
    }

    /**
     * O cliente de pergunta e resposta do site (vinculo, diario), ou vazio se a integracao esta
     * desligada. Outros mods (o {@code aurorion-diario}) usam por aqui, sem repetir credencial.
     */
    public static Optional<SiteApi> site() {
        return Optional.ofNullable(site);
    }

    /** O {@link GameFacts.Sink} da integracao. Thread do servidor: serializa e enfileira, nada mais. */
    public static void accept(GameFacts.Fact fact) {
        Outbox current = outbox;
        if (current == null) return;
        String json = FactJson.encode(fact.eventId(), fact.type(), fact.occurredAt(), fact.profile(),
                fact.character(), fact.sourceKind(), fact.sourceId(), fact.payload());
        current.offer(json);
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        stop();
        ServerDiagnostics.register("Integração", FactBridge::diagnostics);
        state = "desativada por configuração";
        if (!IntegracaoConfig.ENABLED.get()) return;

        MinecraftServer server = event.getServer();
        URI endpoint = endpoint(IntegracaoConfig.URL.get());
        String token = IntegracaoConfig.TOKEN.get().strip();
        if (endpoint == null || token.isEmpty()) {
            state = "configuração de URL/credencial incompleta";
            AurorionIntegracao.LOGGER.warn("Integracao habilitada, mas sem url/token validos em config/aurorion/integracao-startup.toml; nada sera enviado");
            return;
        }

        IngestClient client = new IngestClient(URI.create(endpoint + "/events/batch"), token, userAgent());
        site = new SiteApi(endpoint, token, userAgent());
        Path spool = server.getWorldPath(LevelResource.ROOT).resolve(SPOOL_DIR).resolve(SPOOL_FILE);
        long maxBytes = IntegracaoConfig.SPOOL_MAX_MIB.get() * 1024L * 1024L;
        outbox = new Outbox(spool, maxBytes, client, new Outbox.Log() {
            @Override
            public void info(String message) {
                AurorionIntegracao.LOGGER.info(message);
            }

            @Override
            public void warn(String message) {
                AurorionIntegracao.LOGGER.warn(message);
            }
        });
        state = "ativa";
        AurorionIntegracao.LOGGER.info("Integracao com o site ativa ({})", client.describe());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        stop();
    }

    private static void stop() {
        ServerDiagnostics.unregister("Integração");
        state = "parada";
        Outbox current = outbox;
        outbox = null;
        if (current != null) current.close();
        SiteApi api = site;
        site = null;
        if (api != null) api.close();
    }

    private static List<String> diagnostics() {
        var lines = new ArrayList<String>();
        lines.add("Estado: " + state);
        Outbox current = outbox;
        if (current != null) {
            var facts = current.diagnostics();
            lines.add("Fatos na fila: " + facts.queued() + "/" + facts.queueCapacity());
            lines.add("Fatos pendentes de confirmação: " + facts.pending());
            lines.add("Spool em memória: " + facts.pendingBytes() + "/" + facts.spoolCapacityBytes() + " bytes");
            lines.add("Próxima tentativa em: " + ((facts.retryInMillis() + 999) / 1_000) + " s");
            lines.add("Resolvidos/descartados desde o início: " + facts.resolved() + "/" + facts.dropped());
            lines.add("Último resultado: " + facts.lastResult());
            long now = System.currentTimeMillis();
            if (facts.lastAttemptAt() > 0) lines.add("Última tentativa há: "
                    + Math.max(0, now - facts.lastAttemptAt()) / 1_000 + " s");
            if (facts.lastSuccessAt() > 0) lines.add("Última confirmação há: "
                    + Math.max(0, now - facts.lastSuccessAt()) / 1_000 + " s");
            lines.add("Estado recente do disco: " + (facts.diskHealthy() ? "sem falhas registradas" : "última operação falhou; conferir log"));
        }
        SiteApi api = site;
        if (api != null) {
            var http = api.diagnostics();
            lines.add("Chamadas HTTP em andamento: " + http.inFlight());
            lines.add("Tarefas HTTP na fila: " + http.executorQueued() + "/" + http.executorCapacity());
            lines.add("Falhas de transporte desde o início: " + http.transportFailures());
            lines.add("Último resultado HTTP: " + http.lastResult());
        }
        return lines;
    }

    /**
     * Aceita http(s). HTTP puro so faz sentido em rede interna (mesmo host ou rede Docker): avisa,
     * porque o token viajaria aberto.
     */
    @Nullable
    static URI endpoint(String raw) {
        String value = raw == null ? "" : raw.strip();
        if (value.isEmpty()) return null;
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (uri.getHost() == null || !(scheme.equals("https") || scheme.equals("http"))) return null;
            if (scheme.equals("http") && !internalHost(uri.getHost())) {
                AurorionIntegracao.LOGGER.warn("Integracao usando HTTP sem TLS para {}: o token trafega aberto. Prefira https.", uri.getHost());
            }
            return SiteApi.normalizeBase(uri);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static boolean internalHost(String host) {
        String h = host.toLowerCase(Locale.ROOT);
        return h.equals("localhost") || h.startsWith("127.") || h.equals("[::1]") || !h.contains(".");
    }

    private static String userAgent() {
        String version = ModList.get().getModContainerById(AurorionIntegracao.MOD_ID)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("dev");
        return "aurorion-integracao/" + version;
    }
}
