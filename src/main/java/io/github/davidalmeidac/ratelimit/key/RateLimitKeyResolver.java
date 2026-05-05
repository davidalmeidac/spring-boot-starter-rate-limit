package io.github.davidalmeidac.ratelimit.key;

import org.aspectj.lang.ProceedingJoinPoint;

/**
 * Resolves the rate-limit bucket identifier for a given method invocation.
 *
 * <p>The default implementation reads the client IP from the current HTTP
 * request. Applications can replace it by exposing their own bean of this
 * type (the autoconfiguration backs off when one is present).
 *
 * @author David Almeida
 * @since 0.1.0
 */
@FunctionalInterface
public interface RateLimitKeyResolver {

    /**
     * Computes the bucket key for {@code joinPoint}. Implementations should
     * be deterministic and inexpensive — they are called on every invocation
     * of every rate-limited method.
     *
     * @param joinPoint the AOP join point of the rate-limited method
     * @return a non-null, non-empty key
     */
    String resolve(ProceedingJoinPoint joinPoint);
}
