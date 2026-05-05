package io.github.davidalmeidac.ratelimit.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the rate-limit starter.
 *
 * <p>Bind your application properties under the {@code rate-limit.*} prefix:
 *
 * <pre>{@code
 * rate-limit:
 *   enabled: true
 *   backend: in-memory     # or "redis"
 *   redis:
 *     key-prefix: "rl:"
 * }</pre>
 *
 * @author David Almeida
 * @since 0.1.0
 */
@ConfigurationProperties(prefix = "rate-limit")
public class RateLimitProperties {

    /**
     * Whether the rate-limit starter is enabled. When {@code false}, no
     * aspect is registered and {@code @RateLimit} annotations are ignored.
     */
    private boolean enabled = true;

    /**
     * Backend to use for rate-limit storage.
     */
    private Backend backend = Backend.IN_MEMORY;

    private final Redis redis = new Redis();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Backend getBackend() {
        return backend;
    }

    public void setBackend(Backend backend) {
        this.backend = backend;
    }

    public Redis getRedis() {
        return redis;
    }

    public enum Backend {
        IN_MEMORY,
        REDIS
    }

    public static class Redis {
        /**
         * Prefix prepended to all keys in Redis. Useful when sharing a Redis
         * instance across multiple applications.
         */
        private String keyPrefix = "rl:";

        public String getKeyPrefix() {
            return keyPrefix;
        }

        public void setKeyPrefix(String keyPrefix) {
            this.keyPrefix = keyPrefix;
        }
    }
}
