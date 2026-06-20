package br.com.renan.corebanking.authorization.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import br.com.renan.corebanking.authorization.exception.ResourceLockedException;
import br.com.renan.corebanking.authorization.observability.TransactionAuthorizationMetrics;

class RedisDistributedLockServiceTest {
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private TransactionAuthorizationMetrics metrics;
    private RedisDistributedLockService service;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        metrics = mock(TransactionAuthorizationMetrics.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        service = new RedisDistributedLockService(redisTemplate, metrics);
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
