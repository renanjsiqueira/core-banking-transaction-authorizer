package br.com.renan.corebanking.authorization.service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import br.com.renan.corebanking.authorization.exception.ResourceLockedException;

@Service
public class RedisDistributedLockService {
    private static final Logger log = LoggerFactory.getLogger(RedisDistributedLockService.class);

    private static final RedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('del', KEYS[1]) "
                    + "else return 0 end",
            Long.class);

    private final StringRedisTemplate redisTemplate;
    private final String instanceId = UUID.randomUUID().toString();

    public RedisDistributedLockService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
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
                                 Supplier<T> operation) {
        String owner = instanceId + ":" + UUID.randomUUID();
        long deadline = System.nanoTime() + waitTimeout.toNanos();

        boolean acquired = tryLock(key, owner, ttl);
        while (!acquired && System.nanoTime() < deadline) {
            sleep(retryDelay);
            acquired = tryLock(key, owner, ttl);
        }

        if (!acquired) {
            throw new ResourceLockedException(key);
        }

        try {
            return operation.get();
        } finally {
            unlock(key, owner);
        }
    }

    private void sleep(Duration retryDelay) {
        try {
            Thread.sleep(Math.max(1, retryDelay.toMillis()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResourceLockedException("interrupted while waiting for lock");
        }
    }
}
