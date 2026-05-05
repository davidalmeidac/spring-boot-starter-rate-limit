package io.github.davidalmeidac.ratelimit.aspect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.stereotype.Service;

import io.github.davidalmeidac.ratelimit.annotation.RateLimit;
import io.github.davidalmeidac.ratelimit.exception.RateLimitExceededException;

/**
 * End-to-end test that wires up the autoconfiguration, an annotated bean,
 * and verifies the aspect actually intercepts.
 */
@SpringBootTest(classes = RateLimitAspectIntegrationTest.TestApp.class)
class RateLimitAspectIntegrationTest {

    @Autowired
    private DemoService demoService;

    @Test
    @DisplayName("intercepts annotated method and throws after limit")
    void enforcesLimit() {
        for (int i = 0; i < 3; i++) {
            assertThat(demoService.protectedCall("user-1")).isEqualTo("ok");
        }
        assertThatThrownBy(() -> demoService.protectedCall("user-1"))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessageContaining("user-1");
    }

    @Test
    @DisplayName("different keys do not interfere")
    void differentKeys() {
        for (int i = 0; i < 3; i++) {
            assertThat(demoService.protectedCall("alpha")).isEqualTo("ok");
        }
        assertThat(demoService.protectedCall("beta")).isEqualTo("ok");
    }

    @Test
    @DisplayName("throwOnLimit=false returns null instead of throwing")
    void silentMode() {
        for (int i = 0; i < 2; i++) {
            assertThat(demoService.silent("x")).isEqualTo("ok");
        }
        assertThat(demoService.silent("x")).isNull();
    }

    @SpringBootApplication
    @EnableAspectJAutoProxy
    static class TestApp {
        @org.springframework.context.annotation.Bean
        DemoService demoService() { return new DemoService(); }
    }

    @Service
    static class DemoService {

        @RateLimit(requests = 3, window = 1, unit = TimeUnit.MINUTES, key = "#userId")
        public String protectedCall(String userId) {
            return "ok";
        }

        @RateLimit(requests = 2, window = 1, unit = TimeUnit.MINUTES, key = "#userId", throwOnLimit = false)
        public String silent(String userId) {
            return "ok";
        }
    }
}
