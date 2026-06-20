package br.com.renan.corebanking.authorization.service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

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
    private final String instanceId = UUID.randomUUID().toString();

    public RedisDistributedLockService(StringRedisTemplate redisTemplate,
                                       TransactionAuthorizationMetrics metrics) {
        this.redisTemplate = redisTemplate;
        this.metrics = metrics;
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
        String owner = instanceId + ":" + UUID.randomUUID();
        long start = System.nanoTime();
        long deadline = start + waitTimeout.toNanos();

        boolean acquired = tryLock(key, owner, ttl);
        int attempt = 0;
        while (!acquired && System.nanoTime() < deadline) {
            sleep(cappedByRemainingWait(
                    calculateFullJitterDelay(retryDelay, maxRetryDelay, attempt++), deadline));
            acquired = tryLock(key, owner, ttl);
        }

        if (!acquired) {
            metrics.recordLockTimeout(key, Duration.ofNanos(System.nanoTime() - start));
            throw new ResourceLockedException(key);
        }
        metrics.recordLockAcquired(key, Duration.ofNanos(System.nanoTime() - start));

        try {
            return operation.get();
        } finally {
            unlock(key, owner);
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
