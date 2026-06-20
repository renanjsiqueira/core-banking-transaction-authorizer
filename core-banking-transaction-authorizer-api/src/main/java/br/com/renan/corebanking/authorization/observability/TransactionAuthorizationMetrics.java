package br.com.renan.corebanking.authorization.observability;

import java.time.Duration;

import org.springframework.stereotype.Component;

import br.com.renan.corebanking.authorization.model.Transaction;
import br.com.renan.corebanking.domain.shared.enums.TransactionType;
import io.micrometer.core.instrument.MeterRegistry;

@Component
public class TransactionAuthorizationMetrics {
    private static final String NONE = "none";

    private final MeterRegistry meterRegistry;

    public TransactionAuthorizationMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordAuthorization(Transaction transaction) {
        meterRegistry.counter("transactions.authorizations.total",
                        "type", transaction.getType().name(),
                        "status", transaction.getStatus().name(),
                        "failure_reason", failureReason(transaction))
                .increment();
    }

    public void recordIdempotentReplay(Transaction transaction) {
        meterRegistry.counter("transactions.idempotency.replays.total",
                        "type", transaction.getType().name(),
                        "status", transaction.getStatus().name())
                .increment();
    }

    public void recordIdempotencyConflict(TransactionType type) {
        meterRegistry.counter("transactions.idempotency.conflicts.total",
                        "type", type.name())
                .increment();
    }

    public void recordAccountNotFound(TransactionType type) {
        meterRegistry.counter("transactions.accounts.not_found.total",
                        "type", type.name())
                .increment();
    }

    public void recordLockAcquired(String key, Duration waitTime) {
        String lockType = lockType(key);
        meterRegistry.counter("transactions.locks.acquired.total",
                        "lock_type", lockType)
                .increment();
        meterRegistry.timer("transactions.locks.wait.duration",
                        "lock_type", lockType,
                        "result", "acquired")
                .record(waitTime);
    }

    public void recordLockTimeout(String key, Duration waitTime) {
        String lockType = lockType(key);
        meterRegistry.counter("transactions.locks.timeouts.total",
                        "lock_type", lockType)
                .increment();
        meterRegistry.timer("transactions.locks.wait.duration",
                        "lock_type", lockType,
                        "result", "timeout")
                .record(waitTime);
    }

    private static String failureReason(Transaction transaction) {
        return transaction.getFailureReason() == null ? NONE : transaction.getFailureReason().name();
    }

    private static String lockType(String key) {
        if (key.startsWith("lock:account:")) {
            return "account";
        }
        if (key.startsWith("lock:transaction:")) {
            return "transaction";
        }
        return "unknown";
    }
}
