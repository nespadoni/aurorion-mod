package com.aurorion.limbo.report;

import com.aurorion.limbo.AurorionLimbo;
import com.aurorion.limbo.config.AuditWebhookConfig;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Empurra cada evento do Limbo para um webhook, se houver um configurado.
 *
 * <h2>Por que webhook, se o pedido era RCON</h2>
 *
 * <p>Porque RCON nao faz isto, e nao e questao de esforco: <b>RCON e de mao unica, e a mao e a de
 * fora</b>. Um cliente se conecta ao servidor e manda um comando; o servidor responde e pronto. Ele
 * nao abre conexao RCON com ninguem, entao "avisar o Discord no instante em que alguem atravessou a
 * Porta" nao e uma coisa que RCON saiba fazer.
 *
 * <p>As duas metades do que foi pedido continuam existindo, cada uma pelo caminho que funciona:
 *
 * <ul>
 *   <li><b>Empurrar na hora</b> — esta classe.</li>
 *   <li><b>Puxar quando quiser</b> — {@code /limbo relatorio}, que o bot roda por RCON e recebe de
 *       volta texto estavel em {@code chave=valor}. Essa metade e RCON puro e nao precisa de mais
 *       nada.</li>
 * </ul>
 *
 * <p>Um bot que so queira um painel "quem esta no Limbo agora" pode ignorar esta classe inteira e
 * fazer polling. O webhook existe para o caso em que <em>o instante</em> importa — e, para a Porta do
 * Esquecido, importa: e o gancho de RP nascendo.
 *
 * <h2>Por que nada disto encosta na thread do servidor</h2>
 *
 * <p>Uma chamada HTTP tem latencia imprevisivel e um destino que pode simplesmente nao responder.
 * Fazer isso no tick e trocar um recurso de staff por travadas de servidor. Entao: uma thread
 * daemon, fila limitada, e descarte silencioso quando ela enche. Um webhook fora do ar vira aviso no
 * log — nunca lag, e nunca memoria crescendo sem limite.
 */
public final class DiscordSink {
    private static final int QUEUE_LIMIT = 64;

    @Nullable
    private static volatile DeliverySession session;

    private DiscordSink() {
    }

    public static void push(AuditEvent event) {
        String url = AuditWebhookConfig.WEBHOOK_URL.get();
        if (url == null || url.isBlank()) return;
        if (!secure(url)) return;

        DeliverySession owner = session();
        try {
            owner.pool.execute(() -> send(owner, url, event));
        } catch (RejectedExecutionException e) {
            // Fila cheia: o destino esta lento ou morto. A auditoria em disco ja tem o evento, entao
            // perder o aviso do Discord e o preco certo a pagar — o contrario seria acumular tarefas
            // ate estourar a memoria do servidor por causa de um webhook.
            AurorionLimbo.LOGGER.warn("Webhook do Limbo esta atrasado; evento descartado: {}", event.toLine());
        }
    }

    /**
     * So {@code https} com host. A URL carrega o token do webhook: mandar por {@code http} poe a
     * credencial em texto claro em cada salto da rede, e um esquema estranho ({@code file:},
     * {@code ftp:}) nem e um webhook. A config e lida uma vez por evento, entao o aviso sai uma vez
     * por valor recusado, e nunca com a URL (ela e o segredo).
     */
    static boolean secure(String url) {
        if (url.equals(rejectedUrl)) return false;
        boolean ok;
        try {
            URI uri = URI.create(url.trim());
            ok = "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null && !uri.getHost().isBlank();
        } catch (IllegalArgumentException e) {
            ok = false;
        }
        if (!ok) {
            rejectedUrl = url;
            AurorionLimbo.LOGGER.warn("Webhook do Limbo ignorado: a URL precisa ser https:// com host valido"
                    + " (config STARTUP auditoria). Nenhum evento sera enviado ate corrigir.");
        }
        return ok;
    }

    /** Intervalo minimo entre duas entregas: 2 s, abaixo do limite de 30/min por canal do Discord. */
    private static final long MIN_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(2);

    /** Teto de espera pedido pelo destino num 429. Acima disso o evento e descartado. */
    private static final long MAX_RETRY_AFTER_MILLIS = 60_000L;

    @Nullable private static volatile String rejectedUrl;

    private static void send(DeliverySession owner, String url, AuditEvent event) {
        try {
            HttpClient client = owner.http();
            if (client == null) return;
            JsonObject body = event.toJson();
            // "content" e o que o Discord renderiza. O resto dos campos viaja junto no mesmo objeto:
            // o Discord ignora o que nao conhece, e um endpoint proprio recebe o evento estruturado
            // sem precisar de um formato so dele.
            body.addProperty("content", "**[Limbo]** " + event.toLine());
            JsonObject mentions = new JsonObject();
            mentions.add("parse", new JsonArray());
            body.add("allowed_mentions", mentions);

            HttpRequest request = HttpRequest.newBuilder(URI.create(url.trim()))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();

            // Uma thread so entrega, entao o ritmo e garantido aqui: dormir nesta thread nunca toca o
            // tick, e a fila limitada descarta o excedente se o ritmo nao der conta.
            for (int attempt = 0; attempt < 2; attempt++) {
                owner.pace();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                owner.honorBucket(response);
                if (response.statusCode() != 429) {
                    if (response.statusCode() >= 300) {
                        AurorionLimbo.LOGGER.warn("Webhook do Limbo respondeu {}", response.statusCode());
                    }
                    return;
                }
                long wait = retryAfterMillis(response);
                if (wait > MAX_RETRY_AFTER_MILLIS) break;
                owner.delay(wait);
            }
            AurorionLimbo.LOGGER.warn("Webhook do Limbo limitou a taxa (429); evento descartado: {}", event.toLine());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            // Excecoes HTTP podem incluir a URI inteira, que contem o token do webhook.
            AurorionLimbo.LOGGER.warn("Falha ao enviar evento do Limbo ao webhook ({})", e.getClass().getSimpleName());
        }
    }

    /**
     * Quanto o destino pediu para esperar num 429.
     *
     * <p>O Discord manda {@code retry_after} em segundos (fracionario) no corpo e {@code Retry-After}
     * no cabecalho. Corpo primeiro, cabecalho depois, e 5 s se nenhum vier legivel — melhor esperar
     * de mais que martelar um destino que ja disse "devagar".
     */
    static long retryAfterMillis(HttpResponse<String> response) {
        try {
            String text = response.body();
            if (text != null && !text.isBlank()) {
                var json = com.google.gson.JsonParser.parseString(text);
                if (json.isJsonObject() && json.getAsJsonObject().has("retry_after")) {
                    double seconds = json.getAsJsonObject().get("retry_after").getAsDouble();
                    if (seconds >= 0) return (long) Math.ceil(seconds * 1000.0D);
                }
            }
        } catch (RuntimeException ignored) {
            // Corpo nao e JSON: cai para o cabecalho.
        }
        var header = response.headers().firstValue("Retry-After");
        if (header.isPresent()) {
            try {
                return (long) Math.ceil(Double.parseDouble(header.get().trim()) * 1000.0D);
            } catch (NumberFormatException ignored) {
                // Data HTTP em vez de segundos: usa o padrao.
            }
        }
        return 5_000L;
    }

    private static DeliverySession session() {
        DeliverySession current = session;
        if (current != null) return current;

        synchronized (DiscordSink.class) {
            if (session == null) session = new DeliverySession();
            return session;
        }
    }

    /** A tarefa retém o dono da fila; nunca resolve cliente HTTP de outro mundo. */
    private static final class DeliverySession {
        private final ThreadPoolExecutor pool = new ThreadPoolExecutor(
                        1, 1, 30L, TimeUnit.SECONDS,
                        new ArrayBlockingQueue<>(QUEUE_LIMIT),
                        runnable -> {
                            Thread thread = new Thread(runnable, "aurorion-limbo-webhook");
                            // Daemon: uma entrega pendente nunca segura o desligamento do servidor.
                            thread.setDaemon(true);
                            return thread;
                        });
        @Nullable private HttpClient client;
        private boolean closed;
        /** Quando a proxima entrega pode sair, em {@code System.nanoTime}. So a thread de entrega mexe. */
        private volatile long nextSendNanos;

        private DeliverySession() {
            pool.allowCoreThreadTimeOut(true);
        }

        /** Espera ate a proxima entrega permitida e reserva o intervalo minimo para a seguinte. */
        private void pace() throws InterruptedException {
            long wait = nextSendNanos - System.nanoTime();
            if (wait > 0) TimeUnit.NANOSECONDS.sleep(wait);
            nextSendNanos = System.nanoTime() + MIN_INTERVAL_NANOS;
        }

        /** Empurra a proxima entrega para daqui a {@code millis}. */
        private void delay(long millis) {
            nextSendNanos = Math.max(nextSendNanos, System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(Math.max(0L, millis)));
        }

        /**
         * Respeita o balde que o Discord anuncia em toda resposta: com {@code X-RateLimit-Remaining: 0},
         * a proxima entrega espera {@code X-RateLimit-Reset-After} segundos em vez de tomar um 429.
         */
        private void honorBucket(HttpResponse<?> response) {
            var remaining = response.headers().firstValue("X-RateLimit-Remaining");
            var resetAfter = response.headers().firstValue("X-RateLimit-Reset-After");
            if (remaining.isEmpty() || resetAfter.isEmpty() || !"0".equals(remaining.get().trim())) return;
            try {
                double seconds = Double.parseDouble(resetAfter.get().trim());
                delay(Math.min(MAX_RETRY_AFTER_MILLIS, (long) Math.ceil(seconds * 1000.0D)));
            } catch (NumberFormatException ignored) {
                // Cabecalho estranho: o intervalo minimo ja vale.
            }
        }

        @Nullable
        private synchronized HttpClient http() {
            if (closed) return null;
            if (client == null) {
                client = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(5))
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build();
            }
            return client;
        }

        private synchronized void close() {
            closed = true;
            pool.shutdownNow();
            if (client != null) client.shutdownNow();
        }
    }

    /**
     * Solta as threads no desligamento.
     *
     * <p>Sem isto, um servidor integrado que abre e fecha mundos deixaria uma thread e um cliente
     * HTTP por mundo aberto — o mesmo tipo de vazamento entre mundos que o {@code SavedDataAccess}
     * do core resolve para os dados.
     */
    public static synchronized void shutdown() {
        DeliverySession current = session;
        session = null;
        if (current != null) current.close();
    }
}
