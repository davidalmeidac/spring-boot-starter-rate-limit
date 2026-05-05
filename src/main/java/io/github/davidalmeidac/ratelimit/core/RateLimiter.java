package io.github.davidalmeidac.ratelimit.core;

import java.time.Duration;

/**
 * A rate limiter capable of deciding whether a request keyed by some identifier
 * should be allowed or rejected.
 *
 * <p>Implementations must be safe for concurrent use across threads.
 * The semantics of {@link #tryAcquire(String, int, Duration)} are
 * "permit if and only if at most {@code maxRequests} have been recorded for
 * {@code key} within the most recent {@code window}".
 *
 * @author David Almeida
 * @since 0.1.0
 */
public interface RateLimiter {

    /**
     * Attempts to record a single request against {@code key} and returns
     * whether the request should be allowed.
     *
     * @param key         the bucket identifier (e.g. IP address, user id)
     * @param maxRequests maximum requests allowed in the window
     * @param window      length of the rolling window
     * @return {@code true} if the request is allowed, {@code false} if the
     *         limit has been exceeded for this window
     */
    boolean tryAcquire(String key, int maxRequests, Duration window);

    /**
     * Returns the remaining quota for {@code key} within the current window.
     * Implementations may approximate this value for performance reasons.
     */
    long remaining(String key, int maxRequests, Duration window);

    /**
     * Resets the bucket for {@code key}. Primarily useful for tests and
     * administrative operations.
     */
    void reset(String key);
}
