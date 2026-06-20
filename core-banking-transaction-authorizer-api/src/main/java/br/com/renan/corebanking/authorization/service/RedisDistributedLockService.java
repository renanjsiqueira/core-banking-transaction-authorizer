package br.com.renan.corebanking.authorization.service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import br.com.renan.corebanking.authorization.config.RedisLockProperties;
import br.com.renan.corebanking.authorization.exception.LockUnavailableException;
import br.com.renan.corebanking.authorization.exception.ResourceLockedException;
import br.com.renan.corebanking.authorization.observability.TransactionAuthorizationMetrics;

@Service
public class RedisDistributedLockService {
    private static final Logger log = LoggerFactory.getLogger(RedisDistributedLockService.class);

    private static final RedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('del', KEYS[1]) "
                    + "else return 0 end",
            Long.class);

    private final StringRedisTemplate redisTemplate;
    private final TransactionAuthorizationMetrics metrics;
    private final RedisLockProperties properties;
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private final AtomicLong circuitOpenUntilNanos = new AtomicLong();
    private final String instanceId = UUID.randomUUID().toString();

    public RedisDistributedLockService(StringRedisTemplate redisTemplate,
                                       TransactionAuthorizationMetrics metrics,
                                       RedisLockProperties properties) {
        this.redisTemplate = redisTemplate;
        this.metrics = metrics;
        this.properties = properties;
    }

    public boolean tryLock(String key, String owner, Duration ttl) {
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, owner, ttl);
        return Boolean.TRUE.equals(acquired);
    }

    public void unlock(String key, String owner) {
        redisTemplate.execute(UNLOCK_SCRIPT, List.of(key), owner);
    }

    public <T> T executeWithLock(String key,
                                 Duration ttl,
                                 Duration waitTimeout,
                                 Duration retryDelay,
                                 Duration maxRetryDelay,
                                 Supplier<T> operation) {
        if (isCircuitOpen()) {
            return bypassOrFailClosed(key, "circuit_open", null, operation);
        }

        String owner = instanceId + ":" + UUID.randomUUID();
        long start = System.nanoTime();
        long deadline = start + waitTimeout.toNanos();

        boolean acquired;
        try {
            acquired = tryLock(key, owner, ttl);
        } catch (RuntimeException ex) {
            return handleLockInfrastructureFailure(key, "acquire", ex, operation);
        }

        int attempt = 0;
        while (!acquired && System.nanoTime() < deadline) {
            sleep(cappedByRemainingWait(
                    calculateFullJitterDelay(retryDelay, maxRetryDelay, attempt++), deadline));
            try {
                acquired = tryLock(key, owner, ttl);
            } catch (RuntimeException ex) {
                return handleLockInfrastructureFailure(key, "acquire", ex, operation);
            }
        }

        if (!acquired) {
            metrics.recordLockTimeout(key, Duration.ofNanos(System.nanoTime() - start));
            throw new ResourceLockedException(key);
        }
        recordCircuitSuccess();
        metrics.recordLockAcquired(key, Duration.ofNanos(System.nanoTime() - start));

        try {
            return operation.get();
        } finally {
            try {
                unlock(key, owner);
            } catch (RuntimeException ex) {
                handleUnlockFailure(key, ex);
            }
        }
    }

    private <T> T handleLockInfrastructureFailure(String key,
                                                  String redisOperation,
                                                  RuntimeException ex,
                                                  Supplier<T> operation) {
        recordCircuitFailure(key, redisOperation);
        return bypassOrFailClosed(key, "redis_unavailable", ex, operation);
    }

    private void handleUnlockFailure(String key, RuntimeException ex) {
        recordCircuitFailure(key, "unlock");
        log.warn("Redis-compatible lock unlock failed; key will expire by TTL: key={}, errorType={}, error={}",
                key, ex.getClass().getSimpleName(), ex.getMessage());
    }

    private <T> T bypassOrFailClosed(String key,
                                     String reason,
                                     RuntimeException ex,
                                     Supplier<T> operation) {
        if (properties.getCircuitBreaker().getFallback()
                == RedisLockProperties.CircuitBreakerFallback.FAIL_OPEN) {
            metrics.recordLockBypassed(key, reason);
            if (ex == null) {
                log.warn("Redis-compatible lock bypassed: key={}, reason={}", key, reason);
            } else {
                log.warn("Redis-compatible lock unavailable; bypassing lock: key={}, reason={}, errorType={}, error={}",
                        key, reason, ex.getClass().getSimpleName(), ex.getMessage());
            }
            return operation.get();
        }

        throw new LockUnavailableException("Distributed lock unavailable for key " + key, ex);
    }

    private boolean isCircuitOpen() {
        RedisLockProperties.CircuitBreaker circuitBreaker = properties.getCircuitBreaker();
        if (!circuitBreaker.isEnabled()) {
            return false;
        }

        long openUntil = circuitOpenUntilNanos.get();
        if (openUntil == 0) {
            return false;
        }

        long now = System.nanoTime();
        if (now < openUntil) {
            return true;
        }

        if (circuitOpenUntilNanos.compareAndSet(openUntil, 0)) {
            log.info("Redis-compatible lock circuit breaker moved to half-open trial state");
        }
        return false;
    }

    private void recordCircuitSuccess() {
        if (!properties.getCircuitBreaker().isEnabled()) {
            return;
        }
        consecutiveFailures.set(0);
        circuitOpenUntilNanos.set(0);
    }

    private void recordCircuitFailure(String key, String redisOperation) {
        metrics.recordLockInfrastructureFailure(key, redisOperation);

        RedisLockProperties.CircuitBreaker circuitBreaker = properties.getCircuitBreaker();
        if (!circuitBreaker.isEnabled()) {
            return;
        }

        int threshold = Math.max(1, circuitBreaker.getFailureThreshold());
        int failures = consecutiveFailures.incrementAndGet();
        if (failures < threshold) {
            return;
        }

        long now = System.nanoTime();
        long previousOpenUntil = circuitOpenUntilNanos.getAndSet(
                now + positiveNanos(circuitBreaker.getOpenDuration()));
        if (previousOpenUntil == 0 || now >= previousOpenUntil) {
            metrics.recordLockCircuitOpened(redisOperation);
            log.warn("Redis-compatible lock circuit breaker opened: operation={}, failures={}, fallback={}",
                    redisOperation, failures, circuitBreaker.getFallback());
        }
    }

    static Duration calculateFullJitterDelay(Duration retryDelay, Duration maxRetryDelay, int attempt) {
        long capNanos = calculateBackoffCap(retryDelay, maxRetryDelay, attempt).toNanos();
        long jitterNanos = ThreadLocalRandom.current().nextLong(capNanos + 1);
        return Duration.ofNanos(jitterNanos);
    }

    static Duration calculateBackoffCap(Duration retryDelay, Duration maxRetryDelay, int attempt) {
        long baseNanos = positiveNanos(retryDelay);
        long maxNanos = Math.max(baseNanos, positiveNanos(maxRetryDelay));
        long capNanos = baseNanos;

        for (int i = 0; i < attempt && capNanos < maxNanos; i++) {
            if (capNanos > maxNanos / 2) {
                capNanos = maxNanos;
            } else {
                capNanos *= 2;
            }
        }
        return Duration.ofNanos(capNanos);
    }

    private static long positiveNanos(Duration duration) {
        return Math.max(1, duration.toNanos());
    }

    private static Duration cappedByRemainingWait(Duration delay, long deadlineNanos) {
        long remainingNanos = deadlineNanos - System.nanoTime();
        if (remainingNanos <= 0) {
            return Duration.ZERO;
        }
        return delay.compareTo(Duration.ofNanos(remainingNanos)) <= 0
                ? delay
                : Duration.ofNanos(remainingNanos);
    }

    private void sleep(Duration delay) {
        if (delay.isZero() || delay.isNegative()) {
            return;
        }
        try {
            long millis = delay.toMillis();
            int nanos = (int) delay.minusMillis(millis).toNanos();
            Thread.sleep(millis, nanos);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResourceLockedException("interrupted while waiting for lock");
        }
    }
}
