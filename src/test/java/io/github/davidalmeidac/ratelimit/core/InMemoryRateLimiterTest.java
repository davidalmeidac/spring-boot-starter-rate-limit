package io.github.davidalmeidac.ratelimit.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InMemoryRateLimiterTest {

    @Test
    @DisplayName("permits up to maxRequests within the window, rejects after")
    void enforcesLimit() {
        var limiter = new InMemoryRateLimiter();
        for (int i = 0; i < 5; i++) {
            assertThat(limiter.tryAcquire("ip-1", 5, Duration.ofSeconds(10))).isTrue();
        }
        assertThat(limiter.tryAcquire("ip-1", 5, Duration.ofSeconds(10))).isFalse();
    }

    @Test
    @DisplayName("different keys do not interfere with each other")
    void isolatesKeys() {
        var limiter = new InMemoryRateLimiter();
        for (int i = 0; i < 3; i++) {
            assertThat(limiter.tryAcquire("user-a", 3, Duration.ofMinutes(1))).isTrue();
        }
        assertThat(limiter.tryAcquire("user-a", 3, Duration.ofMinutes(1))).isFalse();
        assertThat(limiter.tryAcquire("user-b", 3, Duration.ofMinutes(1))).isTrue();
    }

    @Test
    @DisplayName("requests outside the window are evicted on the next call")
    void slidingWindow() {
        var clock = new MovableClock(Instant.parse("2025-01-01T00:00:00Z"));
        var limiter = new InMemoryRateLimiter(clock);

        for (int i = 0; i < 3; i++) {
            assertThat(limiter.tryAcquire("k", 3, Duration.ofSeconds(10))).isTrue();
        }
        assertThat(limiter.tryAcquire("k", 3, Duration.ofSeconds(10))).isFalse();

        // advance past the window
        clock.advance(Duration.ofSeconds(11));

        assertThat(limiter.tryAcquire("k", 3, Duration.ofSeconds(10))).isTrue();
    }

    @Test
    @DisplayName("remaining reflects the live bucket")
    void remaining() {
        var limiter = new InMemoryRateLimiter();
        assertThat(limiter.remaining("k", 5, Duration.ofMinutes(1))).isEqualTo(5);
        limiter.tryAcquire("k", 5, Duration.ofMinutes(1));
        limiter.tryAcquire("k", 5, Duration.ofMinutes(1));
        assertThat(limiter.remaining("k", 5, Duration.ofMinutes(1))).isEqualTo(3);
    }

    @Test
    @DisplayName("reset clears the bucket")
    void reset() {
        var limiter = new InMemoryRateLimiter();
        limiter.tryAcquire("k", 1, Duration.ofMinutes(1));
        assertThat(limiter.tryAcquire("k", 1, Duration.ofMinutes(1))).isFalse();
        limiter.reset("k");
        assertThat(limiter.tryAcquire("k", 1, Duration.ofMinutes(1))).isTrue();
    }

    @Test
    @DisplayName("evictIdle removes buckets older than maxAge")
    void evictIdle() {
        var clock = new MovableClock(Instant.parse("2025-01-01T00:00:00Z"));
        var limiter = new InMemoryRateLimiter(clock);

        limiter.tryAcquire("active", 10, Duration.ofMinutes(1));
        limiter.tryAcquire("idle", 10, Duration.ofMinutes(1));
        clock.advance(Duration.ofMinutes(2));
        limiter.tryAcquire("active", 10, Duration.ofMinutes(1));

        limiter.evictIdle(Duration.ofMinutes(1));

        assertThat(limiter.size()).isEqualTo(1);
    }

    @Test
    @DisplayName("rejects illegal maxRequests")
    void rejectsZeroMaxRequests() {
        var limiter = new InMemoryRateLimiter();
        assertThatThrownBy(() -> limiter.tryAcquire("k", 0, Duration.ofMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("under concurrent load, the count never exceeds maxRequests")
    void threadSafetyUnderContention() throws InterruptedException {
        int threads = 20;
        int requestsPerThread = 100;
        int max = 50;
        var limiter = new InMemoryRateLimiter();
        var allowed = new AtomicInteger();
        var start = new CountDownLatch(1);
        var done = new CountDownLatch(threads);

        for (int t = 0; t < threads; t++) {
            new Thread(() -> {
                try {
                    start.await();
                    for (int i = 0; i < requestsPerThread; i++) {
                        if (limiter.tryAcquire("contested", max, Duration.ofMinutes(1))) {
                            allowed.incrementAndGet();
                        }
                    }
                } catch (InterruptedException ignored) {
                } finally {
                    done.countDown();
                }
            }).start();
        }

        start.countDown();
        done.await();

        assertThat(allowed.get()).isEqualTo(max);
    }

    /** Step clock for deterministic window tests. */
    private static final class MovableClock extends Clock {
        private Instant now;
        MovableClock(Instant now) { this.now = now; }
        void advance(Duration d) { now = now.plus(d); }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
