package br.com.renan.corebanking.authorization.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.redis-lock")
public class RedisLockProperties {
    private boolean enabled = true;
    private Duration accountLockTtl = Duration.ofSeconds(30);
    private Duration transactionLockTtl = Duration.ofSeconds(30);
    private Duration waitTimeout = Duration.ofSeconds(2);
    private Duration retryDelay = Duration.ofMillis(50);
    private Duration maxRetryDelay = Duration.ofMillis(250);
    private CircuitBreaker circuitBreaker = new CircuitBreaker();

    public enum CircuitBreakerFallback {
        FAIL_OPEN,
        FAIL_CLOSED
    }

    @Getter
    @Setter
    public static class CircuitBreaker {
        private boolean enabled = true;
        private int failureThreshold = 3;
        private Duration openDuration = Duration.ofSeconds(30);
        private CircuitBreakerFallback fallback = CircuitBreakerFallback.FAIL_OPEN;
    }
}
