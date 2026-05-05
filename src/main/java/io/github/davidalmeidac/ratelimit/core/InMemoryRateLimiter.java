package io.github.davidalmeidac.ratelimit.core;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * In-process rate limiter using a sliding window log.
 *
 * <p>Each key holds a small deque of recent request timestamps. On every
 * {@link #tryAcquire(String, int, java.time.Duration)} call, expired entries
 * are evicted, and the request is permitted only if the resulting deque size
 * is below {@code maxRequests}.
 *
 * <p>Memory profile is O(maxRequests) per active key. For very large
 * {@code maxRequests} (think millions), prefer a token-bucket implementation
 * or the Redis-backed variant.
 *
 * <p>This class is thread-safe through per-key locking. Different keys are
 * not contended against each other.
 *
 * @author David Almeida
 * @since 0.1.0
 */
public final class InMemoryRateLimiter implements RateLimiter {

    private final Clock clock;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public InMemoryRateLimiter() {
        this(Clock.systemUTC());
    }

    /**
     * Visible for testing — inject a fixed or step clock.
     */
    public InMemoryRateLimiter(Clock clock) {
        this.clock = clock;
    }

    @Override
    public boolean tryAcquire(String key, int maxRequests, Duration window) {
        if (maxRequests <= 0) {
            throw new IllegalArgumentException("maxRequests must be > 0");
        }
        Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket());
        long now = clock.millis();
        long windowStart = now - window.toMillis();

        bucket.lock.lock();
        try {
            // Evict expired
            while (!bucket.timestamps.isEmpty() && bucket.timestamps.peekFirst() < windowStart) {
                bucket.timestamps.pollFirst();
            }
            if (bucket.timestamps.size() < maxRequests) {
                bucket.timestamps.addLast(now);
                return true;
            }
            return false;
        } finally {
            bucket.lock.unlock();
        }
    }

    @Override
    public long remaining(String key, int maxRequests, Duration window) {
        Bucket bucket = buckets.get(key);
        if (bucket == null) {
            return maxRequests;
        }
        long now = clock.millis();
        long windowStart = now - window.toMillis();

        bucket.lock.lock();
        try {
            while (!bucket.timestamps.isEmpty() && bucket.timestamps.peekFirst() < windowStart) {
                bucket.timestamps.pollFirst();
            }
            return Math.max(0, maxRequests - bucket.timestamps.size());
        } finally {
            bucket.lock.unlock();
        }
    }

    @Override
    public void reset(String key) {
        buckets.remove(key);
    }

    /**
     * Removes buckets whose latest entry is older than {@code maxAge}.
     * Call this periodically to free memory when many keys come and go.
     */
    public void evictIdle(Duration maxAge) {
        long cutoff = clock.millis() - maxAge.toMillis();
        buckets.entrySet().removeIf(e -> {
            Bucket b = e.getValue();
            b.lock.lock();
            try {
                return b.timestamps.isEmpty() || b.timestamps.peekLast() < cutoff;
            } finally {
                b.lock.unlock();
            }
        });
    }

    /**
     * Returns the number of active buckets. Visible for testing and metrics.
     */
    public int size() {
        return buckets.size();
    }

    private static final class Bucket {
        final Deque<Long> timestamps = new ArrayDeque<>();
        final ReentrantLock lock = new ReentrantLock();
    }
}
