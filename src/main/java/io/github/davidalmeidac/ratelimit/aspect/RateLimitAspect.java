package io.github.davidalmeidac.ratelimit.aspect;

import java.lang.reflect.Method;
import java.time.Duration;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import io.github.davidalmeidac.ratelimit.annotation.RateLimit;
import io.github.davidalmeidac.ratelimit.core.RateLimiter;
import io.github.davidalmeidac.ratelimit.exception.RateLimitExceededException;
import io.github.davidalmeidac.ratelimit.key.RateLimitKeyResolver;

/**
 * Spring AOP aspect that intercepts methods (or classes) annotated with
 * {@link RateLimit} and applies the configured {@link RateLimiter}.
 *
 * <p>The advice is type-checked at runtime — it tolerates the annotation on
 * either the method or the declaring class, with the method-level annotation
 * taking precedence.
 *
 * <p>SpEL expressions in {@link RateLimit#key()} are evaluated against a
 * {@link StandardEvaluationContext} populated with the call arguments by name
 * (when {@code -parameters} compilation is enabled) and by position
 * ({@code #args[0]}, etc).
 *
 * @author David Almeida
 * @since 0.1.0
 */
@Aspect
public final class RateLimitAspect {

    private final RateLimiter rateLimiter;
    private final RateLimitKeyResolver fallbackKeyResolver;
    private final ExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    public RateLimitAspect(RateLimiter rateLimiter, RateLimitKeyResolver fallbackKeyResolver) {
        this.rateLimiter = rateLimiter;
        this.fallbackKeyResolver = fallbackKeyResolver;
    }

    @Around("@annotation(io.github.davidalmeidac.ratelimit.annotation.RateLimit) || "
            + "@within(io.github.davidalmeidac.ratelimit.annotation.RateLimit)")
    public Object enforce(ProceedingJoinPoint joinPoint) throws Throwable {
        RateLimit annotation = findAnnotation(joinPoint);
        if (annotation == null) {
            return joinPoint.proceed();
        }

        String key = composeKey(joinPoint, annotation);
        Duration window = Duration.ofMillis(annotation.unit().toMillis(annotation.window()));

        if (rateLimiter.tryAcquire(key, annotation.requests(), window)) {
            return joinPoint.proceed();
        }

        if (annotation.throwOnLimit()) {
            throw new RateLimitExceededException(key, annotation.requests(), window);
        }
        return defaultReturn((MethodSignature) joinPoint.getSignature());
    }

    private RateLimit findAnnotation(ProceedingJoinPoint joinPoint) {
        MethodSignature sig = (MethodSignature) joinPoint.getSignature();
        Method method = sig.getMethod();
        RateLimit onMethod = AnnotationUtils.findAnnotation(method, RateLimit.class);
        if (onMethod != null) {
            return onMethod;
        }
        return AnnotationUtils.findAnnotation(method.getDeclaringClass(), RateLimit.class);
    }

    private String composeKey(ProceedingJoinPoint joinPoint, RateLimit annotation) {
        String resolved = annotation.key().isEmpty()
                ? fallbackKeyResolver.resolve(joinPoint)
                : evaluateSpel(joinPoint, annotation.key());

        if (resolved == null || resolved.isBlank()) {
            resolved = "anonymous";
        }
        return annotation.namespace().isEmpty()
                ? resolved
                : annotation.namespace() + ":" + resolved;
    }

    private String evaluateSpel(ProceedingJoinPoint joinPoint, String spel) {
        MethodSignature sig = (MethodSignature) joinPoint.getSignature();
        Method method = sig.getMethod();
        Object[] args = joinPoint.getArgs();
        String[] paramNames = parameterNameDiscoverer.getParameterNames(method);

        StandardEvaluationContext ctx = new StandardEvaluationContext();
        ctx.setVariable("args", args);
        ctx.setVariable("method", method);
        if (paramNames != null) {
            for (int i = 0; i < paramNames.length && i < args.length; i++) {
                ctx.setVariable(paramNames[i], args[i]);
            }
        }
        Expression expression = parser.parseExpression(spel);
        Object value = expression.getValue(ctx);
        return value == null ? null : value.toString();
    }

    private Object defaultReturn(MethodSignature signature) {
        Class<?> returnType = signature.getReturnType();
        if (!returnType.isPrimitive()) {
            return null;
        }
        if (returnType == boolean.class) return false;
        if (returnType == void.class) return null;
        return 0;
    }
}
