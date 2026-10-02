package com.aurorion.integracao.outbox;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

/** Stops the HTTP subscription before copying bytes beyond the limit, including chunked bodies. */
final class LimitedResponseBody implements HttpResponse.BodySubscriber<String> {
    private final int maxBytes;
    private final ByteArrayOutputStream bytes;
    private final byte[] scratch;
    private final CompletableFuture<String> body = new CompletableFuture<>();
    private Flow.Subscription subscription;

    LimitedResponseBody(int maxBytes) {
        if (maxBytes <= 0) throw new IllegalArgumentException("Invalid response limit");
        this.maxBytes = maxBytes;
        bytes = new ByteArrayOutputStream(Math.min(maxBytes, 4096));
        scratch = new byte[Math.min(maxBytes, 8192)];
    }

    static HttpResponse.BodyHandler<String> utf8(int maxBytes) {
        return info -> new LimitedResponseBody(maxBytes);
    }

    @Override public CompletionStage<String> getBody() { return body; }

    @Override public void onSubscribe(Flow.Subscription incoming) {
        if (subscription != null) { incoming.cancel(); return; }
        subscription = incoming;
        incoming.request(1);
    }

    @Override public void onNext(List<ByteBuffer> buffers) {
        if (body.isDone()) return;
        long incoming = 0;
        for (ByteBuffer buffer : buffers) incoming += buffer.remaining();
        if (incoming > maxBytes - bytes.size()) {
            subscription.cancel();
            bytes.reset();
            body.completeExceptionally(new IOException("HTTP response exceeded " + maxBytes + " bytes"));
            return;
        }
        for (ByteBuffer buffer : buffers) {
            while (buffer.hasRemaining()) {
                int count = Math.min(buffer.remaining(), scratch.length);
                buffer.get(scratch, 0, count);
                bytes.write(scratch, 0, count);
            }
        }
        subscription.request(1);
    }

    @Override public void onError(Throwable error) {
        bytes.reset();
        body.completeExceptionally(error);
    }

    @Override public void onComplete() {
        if (body.isDone()) return;
        body.complete(bytes.toString(StandardCharsets.UTF_8));
        bytes.reset();
    }
}
