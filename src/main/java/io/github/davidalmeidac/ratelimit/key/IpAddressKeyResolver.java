package io.github.davidalmeidac.ratelimit.key;

import org.aspectj.lang.ProceedingJoinPoint;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Resolves the rate-limit key from the current HTTP request's client IP.
 *
 * <p>Honours common proxy headers in this order:
 * {@code X-Forwarded-For}, {@code X-Real-IP}, then the remote address.
 *
 * <p>If no request is bound (e.g. background threads, async invocations),
 * falls back to {@code "anonymous"}.
 *
 * @author David Almeida
 * @since 0.1.0
 */
public final class IpAddressKeyResolver implements RateLimitKeyResolver {

    private static final String FALLBACK_KEY = "anonymous";

    @Override
    public String resolve(ProceedingJoinPoint joinPoint) {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return FALLBACK_KEY;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            // X-Forwarded-For may be a comma-separated list — first entry is the original client
            int comma = forwarded.indexOf(',');
            return comma > 0 ? forwarded.substring(0, comma).trim() : forwarded.trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        String remote = request.getRemoteAddr();
        return remote != null ? remote : FALLBACK_KEY;
    }

    private HttpServletRequest currentRequest() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return attrs == null ? null : attrs.getRequest();
        } catch (IllegalStateException e) {
            return null;
        }
    }
}
