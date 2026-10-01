package com.aurorion.integracao.outbox;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Envia um lote ao backend: {@code POST .../integration/v1/events/batch} com {@code X-Game-Token}.
 *
 * <p>Roda so na thread da {@link Outbox}. O token nunca vai para o log; a URL, so o host.
 */
public final class IngestClient implements Function<List<String>, Attempt> {
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final long DEFAULT_RETRY_AFTER = 30_000L;

    private final URI endpoint;
    private final String token;
    private final String userAgent;
    private final HttpClient client;

    public IngestClient(URI endpoint, String token, String userAgent) {
        this.endpoint = endpoint;
        this.token = token;
        this.userAgent = userAgent;
        this.client = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public Attempt apply(List<String> batch) {
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .header("X-Game-Token", token)
                .header("User-Agent", userAgent)
                .POST(HttpRequest.BodyPublishers.ofString(body(batch), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            return Attempt.retry(0, "rede: " + e.getClass().getSimpleName());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Attempt.retry(0, "interrompido");
        }
        return interpret(response.statusCode(), response.body(),
                response.headers().firstValue("Retry-After").orElse(null), batch.size());
    }

    /** O corpo do lote. Cada linha ja e um objeto JSON valido, entao basta juntar. */
    static String body(List<String> batch) {
        return "{\"events\":[" + String.join(",", batch) + "]}";
    }

    /** Visivel para teste: traduz a resposta HTTP em decisao, sem rede. */
    static Attempt interpret(int status, String body, String retryAfter, int size) {
        if (status == 401 || status == 403) return Attempt.forbidden("HTTP " + status);
        if (status == 429) return Attempt.retry(retryAfterMillis(retryAfter), "HTTP 429");
        if (status < 200 || status >= 300) return Attempt.retry(0, "HTTP " + status);

        JsonArray results;
        try {
            JsonElement root = JsonParser.parseString(body == null ? "" : body);
            results = root.isJsonObject() && root.getAsJsonObject().get("results") instanceof JsonArray array ? array : null;
        } catch (JsonSyntaxException | IllegalStateException e) {
            results = null;
        }
        if (results == null) return Attempt.retry(0, "resposta sem results");

        // O backend responde na mesma ordem do lote, um resultado por fato.
        boolean[] resolved = new boolean[size];
        List<String> rejections = new ArrayList<>();
        for (int i = 0; i < Math.min(size, results.size()); i++) {
            if (!(results.get(i) instanceof JsonObject item)) continue;
            String result = item.has("result") ? item.get("result").getAsString() : "";
            switch (result) {
                case "accepted", "duplicate" -> resolved[i] = true;
                case "rejected" -> {
                    resolved[i] = true;
                    rejections.add(item.has("code") ? item.get("code").getAsString() : "sem_codigo");
                }
                default -> { }
            }
        }
        return new Attempt(Attempt.Kind.DELIVERED, resolved, 0, rejections, "HTTP " + status);
    }

    static long retryAfterMillis(String header) {
        if (header == null) return DEFAULT_RETRY_AFTER;
        try {
            long seconds = Long.parseLong(header.trim());
            return Math.clamp(seconds, 1L, 3600L) * 1000L;
        } catch (NumberFormatException e) {
            return DEFAULT_RETRY_AFTER;
        }
    }

    /** Para o log: host e porta, nunca caminho com segredo nem o token. */
    public String describe() {
        return endpoint.getScheme() + "://" + endpoint.getHost() + (endpoint.getPort() > 0 ? ":" + endpoint.getPort() : "");
    }
}
