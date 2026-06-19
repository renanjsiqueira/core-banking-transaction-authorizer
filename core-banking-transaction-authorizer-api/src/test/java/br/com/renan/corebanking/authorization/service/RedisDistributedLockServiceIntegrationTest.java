package br.com.renan.corebanking.authorization.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@Testcontainers(disabledWithoutDocker = true)
class RedisDistributedLockServiceIntegrationTest {
    @Container
    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    private RedisDistributedLockService service;

    @BeforeEach
    void setUp() {
        LettuceConnectionFactory factory =
                new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        factory.afterPropertiesSet();
        StringRedisTemplate template = new StringRedisTemplate(factory);
        template.afterPropertiesSet();
        service = new RedisDistributedLockService(template);
    }

    @Test
    void acquiresLockOnlyOnceUntilReleased() {
        String key = "lock:account:" + System.nanoTime();

        assertThat(service.tryLock(key, "owner-1", Duration.ofSeconds(30))).isTrue();
        assertThat(service.tryLock(key, "owner-2", Duration.ofSeconds(30))).isFalse();
    }

    @Test
    void unlockWithCorrectOwnerReleasesLock() {
        String key = "lock:account:" + System.nanoTime();
        service.tryLock(key, "owner-1", Duration.ofSeconds(30));

        service.unlock(key, "owner-1");

        assertThat(service.tryLock(key, "owner-2", Duration.ofSeconds(30))).isTrue();
    }

    @Test
    void unlockWithWrongOwnerDoesNotReleaseLock() {
        String key = "lock:account:" + System.nanoTime();
        service.tryLock(key, "owner-1", Duration.ofSeconds(30));

        service.unlock(key, "intruder");

        assertThat(service.tryLock(key, "owner-2", Duration.ofSeconds(30))).isFalse();
    }

    @Test
    void executeWithLockReleasesLockAfterExecution() {
        String key = "lock:account:" + System.nanoTime();

        String result = service.executeWithLock(key,
                Duration.ofSeconds(30), Duration.ofSeconds(1), Duration.ofMillis(20), () -> "ok");

        assertThat(result).isEqualTo("ok");
        assertThat(service.tryLock(key, "owner-after", Duration.ofSeconds(30))).isTrue();
    }
}
