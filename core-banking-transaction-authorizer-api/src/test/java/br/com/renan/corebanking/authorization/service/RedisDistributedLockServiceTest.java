package br.com.renan.corebanking.authorization.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import br.com.renan.corebanking.authorization.config.RedisLockProperties;
import br.com.renan.corebanking.authorization.config.RedisLockProperties.CircuitBreakerFallback;
import br.com.renan.corebanking.authorization.exception.LockUnavailableException;
import br.com.renan.corebanking.authorization.exception.ResourceLockedException;
import br.com.renan.corebanking.authorization.observability.TransactionAuthorizationMetrics;

class RedisDistributedLockServiceTest {
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private TransactionAuthorizationMetrics metrics;
    private RedisLockProperties properties;
    private RedisDistributedLockService service;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        metrics = mock(TransactionAuthorizationMetrics.class);
        properties = new RedisLockProperties();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        service = new RedisDistributedLockService(redisTemplate, metrics, properties);
    }

    @Test
    void tryLockAcquiresWhenKeyIsAbsent() {
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);

        assertThat(service.tryLock("lock:account:1", "owner", Duration.ofSeconds(30))).isTrue();
    }

    @Test
    void tryLockFailsWhenKeyExists() {
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);

        assertThat(service.tryLock("lock:account:1", "owner", Duration.ofSeconds(30))).isFalse();
    }

    @Test
    void unlockRunsScriptWithKeyAndOwner() {
        service.unlock("lock:account:1", "owner-1");

        verify(redisTemplate).execute(any(RedisScript.class), eq(List.of("lock:account:1")), eq("owner-1"));
    }

    @Test
    void executeWithLockRunsOperationWhenAcquired() {
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);

        String result = service.executeWithLock("lock:account:1",
                Duration.ofSeconds(30), Duration.ofSeconds(1), Duration.ofMillis(10),
                Duration.ofMillis(100), () -> "done");

        assertThat(result).isEqualTo("done");
        verify(redisTemplate).execute(any(RedisScript.class), anyList(), any());
        verify(metrics).recordLockAcquired(eq("lock:account:1"), any(Duration.class));
    }

    @Test
    void executeWithLockThrowsWhenLockCannotBeAcquired() {
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);

        assertThatThrownBy(() -> service.executeWithLock("lock:account:1",
                Duration.ofSeconds(30), Duration.ofMillis(100), Duration.ofMillis(10),
                Duration.ofMillis(100), () -> "done"))
                .isInstanceOf(ResourceLockedException.class);
        verify(metrics).recordLockTimeout(eq("lock:account:1"), any(Duration.class));
    }

    @Test
    void executeWithLockBypassesRedisWhenCircuitIsOpenAndFallbackIsFailOpen() {
        properties.getCircuitBreaker().setFailureThreshold(1);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenThrow(new RuntimeException("redis unavailable"));

        String firstResult = service.executeWithLock("lock:account:1",
                Duration.ofSeconds(30), Duration.ofSeconds(1), Duration.ofMillis(10),
                Duration.ofMillis(100), () -> "first");
        String secondResult = service.executeWithLock("lock:account:1",
                Duration.ofSeconds(30), Duration.ofSeconds(1), Duration.ofMillis(10),
                Duration.ofMillis(100), () -> "second");

        assertThat(firstResult).isEqualTo("first");
        assertThat(secondResult).isEqualTo("second");
        verify(valueOperations, times(1)).setIfAbsent(anyString(), anyString(), any(Duration.class));
        verify(metrics).recordLockInfrastructureFailure("lock:account:1", "acquire");
        verify(metrics).recordLockCircuitOpened("acquire");
        verify(metrics).recordLockBypassed("lock:account:1", "redis_unavailable");
        verify(metrics).recordLockBypassed("lock:account:1", "circuit_open");
    }

    @Test
    void executeWithLockFailsClosedWithoutRunningOperationWhenConfigured() {
        properties.getCircuitBreaker().setFailureThreshold(1);
        properties.getCircuitBreaker().setFallback(CircuitBreakerFallback.FAIL_CLOSED);
        AtomicBoolean operationRan = new AtomicBoolean(false);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenThrow(new RuntimeException("redis unavailable"));

        assertThatThrownBy(() -> service.executeWithLock("lock:account:1",
                Duration.ofSeconds(30), Duration.ofSeconds(1), Duration.ofMillis(10),
                Duration.ofMillis(100), () -> {
                    operationRan.set(true);
                    return "done";
                }))
                .isInstanceOf(LockUnavailableException.class);

        assertThat(operationRan).isFalse();
        verify(metrics).recordLockInfrastructureFailure("lock:account:1", "acquire");
        verify(metrics).recordLockCircuitOpened("acquire");
        verify(metrics, never()).recordLockBypassed(anyString(), anyString());
    }

    @Test
    void unlockFailureDoesNotFailCompletedOperation() {
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any()))
                .thenThrow(new RuntimeException("redis unavailable"));

        String result = service.executeWithLock("lock:account:1",
                Duration.ofSeconds(30), Duration.ofSeconds(1), Duration.ofMillis(10),
                Duration.ofMillis(100), () -> "done");

        assertThat(result).isEqualTo("done");
        verify(metrics).recordLockInfrastructureFailure("lock:account:1", "unlock");
    }

    @Test
    void calculateBackoffCapGrowsExponentiallyUntilConfiguredMaximum() {
        Duration retryDelay = Duration.ofMillis(50);
        Duration maxRetryDelay = Duration.ofMillis(250);

        assertThat(RedisDistributedLockService.calculateBackoffCap(retryDelay, maxRetryDelay, 0))
                .isEqualTo(Duration.ofMillis(50));
        assertThat(RedisDistributedLockService.calculateBackoffCap(retryDelay, maxRetryDelay, 1))
                .isEqualTo(Duration.ofMillis(100));
        assertThat(RedisDistributedLockService.calculateBackoffCap(retryDelay, maxRetryDelay, 2))
                .isEqualTo(Duration.ofMillis(200));
        assertThat(RedisDistributedLockService.calculateBackoffCap(retryDelay, maxRetryDelay, 3))
                .isEqualTo(Duration.ofMillis(250));
        assertThat(RedisDistributedLockService.calculateBackoffCap(retryDelay, maxRetryDelay, 10))
                .isEqualTo(Duration.ofMillis(250));
    }

    @Test
    void calculateFullJitterDelayStaysInsideBackoffCap() {
        Duration retryDelay = Duration.ofMillis(50);
        Duration maxRetryDelay = Duration.ofMillis(250);
        Duration cap = RedisDistributedLockService.calculateBackoffCap(retryDelay, maxRetryDelay, 3);

        for (int i = 0; i < 20; i++) {
            Duration delay = RedisDistributedLockService.calculateFullJitterDelay(retryDelay, maxRetryDelay, 3);

            assertThat(delay).isGreaterThanOrEqualTo(Duration.ZERO);
            assertThat(delay).isLessThanOrEqualTo(cap);
        }
    }
}
