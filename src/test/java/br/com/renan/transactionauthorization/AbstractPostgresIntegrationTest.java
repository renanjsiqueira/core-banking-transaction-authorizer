package br.com.renan.transactionauthorization;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for integration tests backed by a real PostgreSQL (Testcontainers)
 * — never H2 — so behaviour matches production closely (UUID/CHAR/NUMERIC types,
 * Flyway migrations, pessimistic locking).
 *
 * <p>Uses the singleton-container pattern: one container is started for the whole
 * test JVM and reused by every subclass; Testcontainers' Ryuk reaps it on exit.
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class AbstractPostgresIntegrationTest {

    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("transaction_authorization")
                    .withUsername("app")
                    .withPassword("app");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
