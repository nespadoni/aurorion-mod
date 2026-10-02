package com.aurorion.integracao.outbox;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Flow;
import static org.junit.jupiter.api.Assertions.*;

class LimitedResponseBodyTest {
    private static final class Subscription implements Flow.Subscription {
        boolean canceled;
        long requested;
        public void request(long count) { requested += count; }
        public void cancel() { canceled = true; }
    }

    @Test void decodesUtf8SplitAcrossChunksAtTheExactByteLimit() {
        byte[] text = "Óbolo".getBytes(StandardCharsets.UTF_8);
        var subscriber = new LimitedResponseBody(text.length);
        var subscription = new Subscription();
        subscriber.onSubscribe(subscription);
        subscriber.onNext(List.of(ByteBuffer.wrap(text, 0, 1)));
        subscriber.onNext(List.of(ByteBuffer.wrap(text, 1, text.length - 1)));
        subscriber.onComplete();
        assertEquals("Óbolo", subscriber.getBody().toCompletableFuture().join());
        assertFalse(subscription.canceled);
    }

    @Test void overflowingChunkIsCanceledBeforeAnyOfItsBytesAreCopied() {
        var subscriber = new LimitedResponseBody(4);
        var subscription = new Subscription();
        subscriber.onSubscribe(subscription);
        subscriber.onNext(List.of(ByteBuffer.wrap(new byte[3])));
        ByteBuffer excess = ByteBuffer.wrap(new byte[2]);
        subscriber.onNext(List.of(excess));
        assertTrue(subscription.canceled);
        assertEquals(0, excess.position());
        assertEquals(2, subscription.requested);
        var failure = assertThrows(CompletionException.class, () -> subscriber.getBody().toCompletableFuture().join());
        assertInstanceOf(IOException.class, failure.getCause());
        subscriber.onComplete();
        assertTrue(subscriber.getBody().toCompletableFuture().isCompletedExceptionally());
    }

    @Test void aggregateBuffersAreLimitedEvenWithoutContentLength() {
        var subscriber = new LimitedResponseBody(4);
        var subscription = new Subscription();
        subscriber.onSubscribe(subscription);
        subscriber.onNext(List.of(ByteBuffer.wrap(new byte[3]), ByteBuffer.wrap(new byte[2])));
        assertTrue(subscription.canceled);
        assertTrue(subscriber.getBody().toCompletableFuture().isCompletedExceptionally());
    }
}
