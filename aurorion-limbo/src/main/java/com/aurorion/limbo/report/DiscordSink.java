package com.aurorion.limbo.report;

import com.aurorion.limbo.AurorionLimbo;
import com.aurorion.limbo.config.LimboConfig;
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
        String url = LimboConfig.WEBHOOK_URL.get();
        if (url == null || url.isBlank()) return;

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

            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();

            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() >= 300) {
                AurorionLimbo.LOGGER.warn("Webhook do Limbo respondeu {}", response.statusCode());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            // Excecoes HTTP podem incluir a URI inteira, que contem o token do webhook.
            AurorionLimbo.LOGGER.warn("Falha ao enviar evento do Limbo ao webhook ({})", e.getClass().getSimpleName());
        }
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

        private DeliverySession() {
            pool.allowCoreThreadTimeOut(true);
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
