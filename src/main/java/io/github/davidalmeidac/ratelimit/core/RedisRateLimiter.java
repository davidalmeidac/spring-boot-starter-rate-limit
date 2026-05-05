package io.github.davidalmeidac.ratelimit.core;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

import org.springframework.data.redis.connection.RedisStringCommands.SetOption;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.types.Expiration;

/**
 * Distributed rate limiter backed by Redis.
 *
 * <p>Uses a sliding-window-counter approach: each request is recorded with
 * a millisecond-precision timestamp into a per-key sorted set. A second pass
 * removes timestamps older than the window before counting.
 *
 * <p>This approach is simpler than a Lua script but issues 4 commands per
 * request. For very high throughput consider replacing with a single
 * EVAL script. The pure-Spring-Data implementation is intentionally favored
 * here for portability across Lettuce and Jedis.
 *
 * <p>All operations key into Redis under the prefix {@link #keyPrefix},
 * which defaults to {@code "rl:"}.
 *
 * @author David Almeida
 * @since 0.1.0
 */
public final class RedisRateLimiter implements RateLimiter {

    private final StringRedisTemplate redis;
    private final Clock clock;
    private final String keyPrefix;

    public RedisRateLimiter(StringRedisTemplate redis) {
        this(redis, Clock.systemUTC(), "rl:");
    }

    public RedisRateLimiter(StringRedisTemplate redis, Clock clock, String keyPrefix) {
        this.redis = redis;
        this.clock = clock;
        this.keyPrefix = keyPrefix;
    }

    @Override
    public boolean tryAcquire(String key, int maxRequests, Duration window) {
        if (maxRequests <= 0) {
            throw new IllegalArgumentException("maxRequests must be > 0");
        }
        String redisKey = keyPrefix + key;
        long now = clock.millis();
        long windowStart = now - window.toMillis();

        // Optimistic insert + count + ttl in a single pipeline
        List<Object> results = redis.executePipelined((org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
            byte[] keyBytes = redisKey.getBytes();
            // 1. Trim expired entries from the sorted set
            connection.zSetCommands().zRemRangeByScore(keyBytes, 0, windowStart);
            // 2. Add current request (score + member must be unique to avoid set semantics)
            connection.zSetCommands().zAdd(
                    keyBytes,
                    now,
                    (now + ":" + Math.random()).getBytes()
            );
            // 3. Count remaining entries
            connection.zSetCommands().zCard(keyBytes);
            // 4. Refresh TTL so empty buckets eventually disappear
            connection.keyCommands().expire(keyBytes, window.toSeconds() + 1);
            return null;
        });

        Long count = (Long) results.get(2);
        return count != null && count <= maxRequests;
    }

    @Override
    public long remaining(String key, int maxRequests, Duration window) {
        String redisKey = keyPrefix + key;
        long now = clock.millis();
        long windowStart = now - window.toMillis();

        Long count = redis.opsForZSet().count(redisKey, windowStart, now);
        if (count == null) {
            return maxRequests;
        }
        return Math.max(0, maxRequests - count);
    }

    @Override
    public void reset(String key) {
        redis.delete(keyPrefix + key);
    }

    /**
     * Marker call used to assert connectivity at startup. Throws if the
     * connection is unhealthy.
     */
    public void ping() {
        redis.execute((org.springframework.data.redis.core.RedisCallback<String>) c -> {
            String pong = c.ping();
            if (!"PONG".equalsIgnoreCase(pong)) {
                throw new IllegalStateException("Unexpected ping response: " + pong);
            }
            return pong;
        });
    }
}
