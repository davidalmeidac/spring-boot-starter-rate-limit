package io.github.davidalmeidac.ratelimit.exception;

import java.time.Duration;

/**
 * Thrown when a rate-limited method is invoked beyond the configured quota.
 *
 * <p>This is a {@link RuntimeException} so it does not pollute method
 * signatures. Spring MVC users typically map it to HTTP 429 with an
 * {@code @ExceptionHandler}; reactive applications can do the same in a
 * {@code WebExceptionHandler}.
 *
 * @author David Almeida
 * @since 0.1.0
 */
public final class RateLimitExceededException extends RuntimeException {

    private final String key;
    private final int maxRequests;
    private final Duration window;

    public RateLimitExceededException(String key, int maxRequests, Duration window) {
        super("Rate limit exceeded: %d req / %s for key '%s'"
                .formatted(maxRequests, window, key));
        this.key = key;
        this.maxRequests = maxRequests;
        this.window = window;
    }

    public String getKey() {
        return key;
    }

    public int getMaxRequests() {
        return maxRequests;
    }

    public Duration getWindow() {
        return window;
    }

    /**
     * Suggested {@code Retry-After} header value (seconds), equal to the
     * configured window. Clients should wait at least this long before retrying.
     */
    public long retryAfterSeconds() {
        return Math.max(1, window.toSeconds());
    }
}
