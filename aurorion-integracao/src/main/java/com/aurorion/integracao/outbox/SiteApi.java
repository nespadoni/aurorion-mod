package com.aurorion.integracao.outbox;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Chamadas de pergunta e resposta ao site (vínculo, diário), sempre assíncronas.
 *
 * <p>Nada aqui roda na thread do servidor: {@link #post} devolve um {@link CompletableFuture} que
 * completa numa thread própria, e quem chama volta para a thread do servidor com
 * {@code server.execute(...)} antes de tocar em jogador ou mundo. Falha de rede não vira exceção
 * propagada: vira {@link Response#transportFailure()}, para quem chama decidir (guardar e tentar de
 * novo, ou avisar o jogador).
 *
 * <p>Sem dependência do Minecraft, como a {@link Outbox}: testável sem o jogo.
 */
public final class SiteApi implements AutoCloseable {
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(12);
    private static final int MAX_BODY = 256 * 1024;

    /** Resposta do site. {@code status} 0 = não houve resposta (rede, timeout). */
    public record Response(int status, @Nullable JsonObject body) {
        public static Response transport() {
            return new Response(0, null);
        }

        public boolean transportFailure() {
            return status == 0 || status >= 500 || status == 429;
        }

        /** O código de resultado que o backend devolve nas rotas do jogo ({@code ok}, {@code conflict}...). */
        public String code() {
            if (body == null || !body.has("code") || !body.get("code").isJsonPrimitive()) return status == 0 ? "unavailable" : "";
            return body.get("code").getAsString();
        }

        @Nullable
        public JsonObject object(String member) {
            return body != null && body.get(member) instanceof JsonObject obj ? obj : null;
        }
    }

    private final URI base;
    private final String token;
    private final String userAgent;
    private final ExecutorService executor;
    private final HttpClient client;

    public SiteApi(URI base, String token, String userAgent) {
        this.base = trimSlash(base);
        this.token = token;
        this.userAgent = userAgent;
        // Duas threads bastam: as chamadas são raras (comandos e salvamentos de diário) e curtas.
        ThreadPoolExecutor pool = new ThreadPoolExecutor(2, 2, 30, TimeUnit.SECONDS, new LinkedBlockingQueue<>(256), runnable -> {
            Thread thread = new Thread(runnable, "aurorion-site-api");
            thread.setDaemon(true);
            return thread;
        });
        pool.allowCoreThreadTimeOut(true);
        this.executor = pool;
        this.client = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .executor(executor)
                .build();
    }

    /** POST JSON em {@code base + path}. Nunca completa com exceção. */
    public CompletableFuture<Response> post(String path, JsonObject body) {
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(base + path))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .header("X-Game-Token", token)
                    .header("User-Agent", userAgent)
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                    .build();
        } catch (IllegalArgumentException e) {
            return CompletableFuture.completedFuture(Response.transport());
        }
        try {
            return client.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                    .handle((response, error) -> error != null || response == null
                            ? Response.transport()
                            : new Response(response.statusCode(), parse(response.body())));
        } catch (RuntimeException e) { // fila cheia ou cliente fechado
            return CompletableFuture.completedFuture(Response.transport());
        }
    }

    /** O endereço base sem segredo, para o log. */
    public String describe() {
        return base.getScheme() + "://" + base.getHost() + (base.getPort() > 0 ? ":" + base.getPort() : "");
    }

    public URI base() {
        return base;
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }

    @Nullable
    static JsonObject parse(String body) {
        if (body == null || body.isEmpty() || body.length() > MAX_BODY) return null;
        try {
            JsonElement element = JsonParser.parseString(body);
            return element.isJsonObject() ? element.getAsJsonObject() : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Aceita a URL base ({@code .../integration/v1}) e também a forma antiga, que apontava direto
     * para {@code .../events/batch}.
     */
    public static URI normalizeBase(URI uri) {
        String path = uri.getPath() == null ? "" : uri.getPath();
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith("/events/batch")) path = path.substring(0, path.length() - "/events/batch".length());
        return trimSlash(uri.resolve(path.isEmpty() ? "/" : path));
    }

    private static URI trimSlash(URI uri) {
        String text = uri.toString();
        while (text.endsWith("/")) text = text.substring(0, text.length() - 1);
        return URI.create(text);
    }
}
