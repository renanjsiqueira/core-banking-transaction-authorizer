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

class RedisDistributedLockServiceTest {
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private RedisDistributedLockService service;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        service = new RedisDistributedLockService(redisTemplate);
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
                Duration.ofSeconds(30), Duration.ofSeconds(1), Duration.ofMillis(10), () -> "done");

        assertThat(result).isEqualTo("done");
        verify(redisTemplate).execute(any(RedisScript.class), anyList(), any());
    }

    @Test
    void executeWithLockThrowsWhenLockCannotBeAcquired() {
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);

        assertThatThrownBy(() -> service.executeWithLock("lock:account:1",
                Duration.ofSeconds(30), Duration.ofMillis(100), Duration.ofMillis(10), () -> "done"))
                .isInstanceOf(ResourceLockedException.class);
    }
}
