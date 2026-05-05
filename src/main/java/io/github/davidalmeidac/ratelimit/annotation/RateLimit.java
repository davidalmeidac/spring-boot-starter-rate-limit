package io.github.davidalmeidac.ratelimit.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * Marks a method or class as rate-limited.
 *
 * <p>When applied to a method, the method is allowed at most {@link #requests()}
 * invocations per the time window defined by {@link #window()} and {@link #unit()}.
 * Once the limit is exceeded, a {@code RateLimitExceededException} is thrown
 * (or, if {@link #throwOnLimit()} is {@code false}, the method returns {@code null}).
 *
 * <p>Keys identify "who" is being rate-limited. By default, the rate limit is keyed
 * by the caller's IP address (extracted from the current HTTP request). You can
 * override this with a SpEL expression in {@link #key()}:
 *
 * <pre>{@code
 * @RateLimit(requests = 100, window = 1, unit = MINUTES, key = "#userId")
 * public Order createOrder(String userId, OrderRequest request) { ... }
 * }</pre>
 *
 * <p>When {@link #key()} is empty, the resolver registered as
 * {@code RateLimitKeyResolver} is used. The default implementation reads the
 * client IP from the current request.
 *
 * @author David Almeida
 * @since 0.1.0
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /**
     * Maximum number of requests allowed within the time window.
     * Must be greater than zero.
     */
    int requests();

    /**
     * Length of the time window. Must be greater than zero.
     */
    long window() default 1;

    /**
     * Unit of the time window. Defaults to {@link TimeUnit#MINUTES}.
     */
    TimeUnit unit() default TimeUnit.MINUTES;

    /**
     * SpEL expression resolved against the method arguments to compute the
     * rate-limit key. When empty, the configured {@code RateLimitKeyResolver}
     * is used (defaults to client IP).
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code "#userId"} — first argument named userId</li>
     *   <li>{@code "#args[0]"} — first argument by position</li>
     *   <li>{@code "#root.methodName + ':' + #userId"} — composed key</li>
     * </ul>
     */
    String key() default "";

    /**
     * Optional namespace prepended to the resolved key. Useful when the same
     * key (e.g. user id) needs separate buckets across different operations.
     */
    String namespace() default "";

    /**
     * Whether to throw {@code RateLimitExceededException} when the limit is
     * exceeded. When {@code false}, the method short-circuits and returns
     * {@code null} (or the default value for primitive return types).
     */
    boolean throwOnLimit() default true;
}
