package br.com.renan.corebanking.authorization.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.com.renan.corebanking.authorization.model.Transaction;
import br.com.renan.corebanking.authorization.support.TransactionTestFactory;
import br.com.renan.corebanking.domain.shared.enums.FailureReason;
import br.com.renan.corebanking.domain.shared.enums.TransactionType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class TransactionAuthorizationMetricsTest {
    private SimpleMeterRegistry meterRegistry;
    private TransactionAuthorizationMetrics metrics;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        metrics = new TransactionAuthorizationMetrics(meterRegistry);
    }

    @Test
    void recordsAuthorizationDecisionWithBusinessTags() {
        Transaction transaction = TransactionTestFactory.rejected(
                UUID.randomUUID(), UUID.randomUUID(), TransactionType.DEBIT,
                "50.00", FailureReason.INSUFFICIENT_FUNDS);

        metrics.recordAuthorization(transaction);

        assertThat(meterRegistry.get("transactions.authorizations.total")
                .tag("type", "DEBIT")
                .tag("status", "FAILED")
                .tag("failure_reason", "INSUFFICIENT_FUNDS")
                .counter()
                .count()).isEqualTo(1.0);
    }

    @Test
    void recordsLockTimeoutWithLockTypeAndWaitTimer() {
        metrics.recordLockTimeout("lock:account:" + UUID.randomUUID(), Duration.ofMillis(25));

        assertThat(meterRegistry.get("transactions.locks.timeouts.total")
                .tag("lock_type", "account")
                .counter()
                .count()).isEqualTo(1.0);
        assertThat(meterRegistry.get("transactions.locks.wait.duration")
                .tag("lock_type", "account")
                .tag("result", "timeout")
                .timer()
                .count()).isEqualTo(1);
    }

    @Test
    void recordsCircuitBreakerAndBypassSignals() {
        metrics.recordLockInfrastructureFailure("lock:transaction:" + UUID.randomUUID(), "acquire");
        metrics.recordLockCircuitOpened("acquire");
        metrics.recordLockBypassed("lock:transaction:" + UUID.randomUUID(), "circuit_open");

        assertThat(meterRegistry.get("transactions.locks.infrastructure_failures.total")
                .tag("lock_type", "transaction")
                .tag("operation", "acquire")
                .counter()
                .count()).isEqualTo(1.0);
        assertThat(meterRegistry.get("transactions.locks.circuit.opened.total")
                .tag("operation", "acquire")
                .counter()
                .count()).isEqualTo(1.0);
        assertThat(meterRegistry.get("transactions.locks.bypassed.total")
                .tag("lock_type", "transaction")
                .tag("reason", "circuit_open")
                .counter()
                .count()).isEqualTo(1.0);
    }
}
