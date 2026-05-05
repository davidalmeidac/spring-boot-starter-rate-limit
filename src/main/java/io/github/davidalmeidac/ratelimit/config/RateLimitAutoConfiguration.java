package io.github.davidalmeidac.ratelimit.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import io.github.davidalmeidac.ratelimit.aspect.RateLimitAspect;
import io.github.davidalmeidac.ratelimit.core.InMemoryRateLimiter;
import io.github.davidalmeidac.ratelimit.core.RateLimiter;
import io.github.davidalmeidac.ratelimit.core.RedisRateLimiter;
import io.github.davidalmeidac.ratelimit.key.IpAddressKeyResolver;
import io.github.davidalmeidac.ratelimit.key.RateLimitKeyResolver;

/**
 * Auto-configuration for the rate-limit starter.
 *
 * <p>The configuration is split into:
 * <ul>
 *   <li>The aspect bean — registered whenever the starter is on the classpath
 *       and {@code rate-limit.enabled=true} (default).</li>
 *   <li>The {@link RateLimiter} bean — defaults to {@link InMemoryRateLimiter}
 *       and switches to {@link RedisRateLimiter} when
 *       {@code rate-limit.backend=redis} <i>and</i>
 *       {@link StringRedisTemplate} is on the classpath.</li>
 *   <li>The {@link RateLimitKeyResolver} bean — defaults to
 *       {@link IpAddressKeyResolver}, applications can override.</li>
 * </ul>
 *
 * <p>All beans are conditional on missing — applications can replace any
 * piece independently without disabling the starter as a whole.
 *
 * @author David Almeida
 * @since 0.1.0
 */
@AutoConfiguration
@EnableConfigurationProperties(RateLimitProperties.class)
@ConditionalOnProperty(prefix = "rate-limit", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RateLimitAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public RateLimitKeyResolver rateLimitKeyResolver() {
        return new IpAddressKeyResolver();
    }

    @Bean
    @ConditionalOnMissingBean
    public RateLimitAspect rateLimitAspect(RateLimiter rateLimiter, RateLimitKeyResolver keyResolver) {
        return new RateLimitAspect(rateLimiter, keyResolver);
    }

    @Configuration
    @ConditionalOnProperty(prefix = "rate-limit", name = "backend", havingValue = "in-memory", matchIfMissing = true)
    static class InMemoryConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public RateLimiter inMemoryRateLimiter() {
            return new InMemoryRateLimiter();
        }
    }

    @Configuration
    @ConditionalOnClass(StringRedisTemplate.class)
    @ConditionalOnProperty(prefix = "rate-limit", name = "backend", havingValue = "redis")
    static class RedisConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public RateLimiter redisRateLimiter(StringRedisTemplate redisTemplate, RateLimitProperties properties) {
            return new RedisRateLimiter(
                    redisTemplate,
                    java.time.Clock.systemUTC(),
                    properties.getRedis().getKeyPrefix()
            );
        }
    }
}
