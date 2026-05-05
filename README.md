# spring-boot-starter-rate-limit

[![Maven Central](https://img.shields.io/maven-central/v/io.github.davidalmeidac/spring-boot-starter-rate-limit?style=flat-square&color=c4471f&label=maven%20central)](https://central.sonatype.com/artifact/io.github.davidalmeidac/spring-boot-starter-rate-limit)
[![CI](https://img.shields.io/github/actions/workflow/status/davidalmeidac/spring-boot-starter-rate-limit/ci.yml?style=flat-square&label=ci)](https://github.com/davidalmeidac/spring-boot-starter-rate-limit/actions/workflows/ci.yml)
[![License](https://img.shields.io/github/license/davidalmeidac/spring-boot-starter-rate-limit?style=flat-square&color=1a1612)](LICENSE)
[![Java](https://img.shields.io/badge/java-17%2B-c4471f?style=flat-square)](https://openjdk.org)
[![Spring Boot](https://img.shields.io/badge/spring--boot-3.3%2B-1a1612?style=flat-square)](https://spring.io/projects/spring-boot)

Annotation-driven rate limiting for Spring Boot 3. Drop in the dependency, add `@RateLimit`,
done. In-memory by default, Redis when you scale out.

```java
@RateLimit(requests = 100, window = 1, unit = MINUTES, key = "#userId")
public Order createOrder(String userId, OrderRequest request) {
    // your business logic
}
```

---

## Why

Most rate-limit libraries for Spring are either too heavy (full API gateway) or too thin
(a class you have to wire by hand). This starter sits in the middle: an annotation, an
auto-configured aspect, and two pluggable backends.

Built on top of standard Spring AOP and Spring Data Redis. No proprietary servers.
No magic. Just one annotation.

## Features

- `@RateLimit` annotation on methods or classes
- **In-memory** sliding-window backend (default, zero config)
- **Redis** backend for multi-instance deployments
- **SpEL keys** — limit by user id, tenant, IP, anything in scope
- **Custom resolvers** — replace the default key resolver with your own bean
- **Namespaces** — keep separate buckets for the same key across operations
- **Silent mode** — return `null` instead of throwing, when that fits better
- **Spring Boot 3.3+ compatible** with auto-configuration imports

## Install

### Maven

```xml
<dependency>
    <groupId>io.github.davidalmeidac</groupId>
    <artifactId>spring-boot-starter-rate-limit</artifactId>
    <version>0.1.0</version>
</dependency>
```

### Gradle

```kotlin
implementation("io.github.davidalmeidac:spring-boot-starter-rate-limit:0.1.0")
```

You also need `spring-boot-starter-aop` (most projects already have it transitively):

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aop</artifactId>
</dependency>
```

## Quick start

```java
import io.github.davidalmeidac.ratelimit.annotation.RateLimit;
import java.util.concurrent.TimeUnit;

@Service
public class OrderService {

    // 100 requests per minute, keyed by IP (default resolver)
    @RateLimit(requests = 100, window = 1, unit = TimeUnit.MINUTES)
    public List<Order> listOrders() { ... }

    // 10 requests per minute per user
    @RateLimit(requests = 10, window = 1, unit = TimeUnit.MINUTES, key = "#userId")
    public Order createOrder(String userId, OrderRequest req) { ... }

    // 5 requests per second, returning null instead of throwing
    @RateLimit(requests = 5, window = 1, unit = TimeUnit.SECONDS,
               key = "#userId", throwOnLimit = false)
    public Optional<Quote> getQuote(String userId) { ... }
}
```

When the limit is exceeded, a `RateLimitExceededException` is thrown. Map it to your
preferred HTTP response:

```java
@RestControllerAdvice
public class RateLimitHandler {

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<?> handle(RateLimitExceededException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(ex.retryAfterSeconds()))
                .body(Map.of("error", "rate_limited", "retryAfter", ex.retryAfterSeconds()));
    }
}
```

## Configuration

All configuration lives under the `rate-limit.*` prefix in your `application.yml`:

```yaml
rate-limit:
  enabled: true              # default true; set false to bypass all annotations
  backend: in-memory         # in-memory (default) or redis
  redis:
    key-prefix: "rl:"        # prefix for keys in Redis
```

### Switching to Redis

Add Spring Data Redis to your project (the starter declares it as `optional`):

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

Then:

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379

rate-limit:
  backend: redis
```

Done. Your existing `@RateLimit` annotations now share state across instances.

## Custom key resolution

Two ways to control which "bucket" a request lands in:

### Per-method, with SpEL

```java
@RateLimit(requests = 50, window = 1, unit = HOURS,
           key = "#tenantId + ':' + #userId")
public void heavyOperation(String tenantId, String userId) { ... }
```

The expression has access to:
- Each parameter by name (when compiled with `-parameters`)
- `#args[i]` for positional access
- `#method`, `#root`

### Globally, with a custom resolver

Provide a bean of `RateLimitKeyResolver`. The default (IP-based) backs off:

```java
@Bean
public RateLimitKeyResolver myResolver() {
    return joinPoint -> {
        // pull current user from your security context
        return SecurityContextHolder.getContext()
                .getAuthentication().getName();
    };
}
```

## Namespaces

Same identifier, different limits per operation:

```java
@RateLimit(requests = 1000, window = 1, unit = HOURS,
           namespace = "search", key = "#userId")
public Page<Item> search(String userId, String q) { ... }

@RateLimit(requests = 50, window = 1, unit = HOURS,
           namespace = "checkout", key = "#userId")
public Receipt checkout(String userId, Cart cart) { ... }
```

Each namespace gets its own bucket — `search:user-1` and `checkout:user-1` are independent.

## How the in-memory backend works

A sliding window log: each key keeps a deque of recent timestamps. On every call, expired
entries are evicted, and the request is permitted only if the resulting size is below
`maxRequests`. Memory profile is `O(maxRequests)` per active key. Per-key locking keeps
contention isolated to the same bucket.

For very large `maxRequests` (think millions), prefer the Redis backend or a token-bucket
implementation.

## How the Redis backend works

A `ZSET` per key, sorted by request timestamps. On each call:

1. `ZREMRANGEBYSCORE` evicts entries older than the window
2. `ZADD` records the current request
3. `ZCARD` returns the count
4. `EXPIRE` refreshes the TTL so empty buckets eventually disappear

All four commands are pipelined, so each call is a single round trip.

## Compatibility

- **Java**: 17+
- **Spring Boot**: 3.3+ (uses the new `AutoConfiguration.imports` mechanism)
- **Spring Data Redis**: 3.x+ (only when using the Redis backend)

## Roadmap

- `0.2.0` — token-bucket backend, prometheus metrics
- `0.3.0` — Lua-based atomic Redis backend, reactive Spring WebFlux support
- `1.0.0` — API frozen, semver guarantees

## License

Apache License 2.0. See [LICENSE](LICENSE).

## Contributing

Issues and PRs welcome. For larger changes, please open an issue first to discuss the
design. Run `./mvnw verify` before submitting.

---

<sub>Built and maintained by [David Almeida](https://github.com/davidalmeidac).</sub>
