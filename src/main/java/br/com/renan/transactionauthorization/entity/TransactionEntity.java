package br.com.renan.transactionauthorization.entity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import br.com.renan.transactionauthorization.enums.FailureReason;
import br.com.renan.transactionauthorization.enums.TransactionStatus;
import br.com.renan.transactionauthorization.enums.TransactionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Record of a single authorization attempt (the ledger entry).
 *
 * <p>The relationship to the account is kept as a plain {@code accountId}
 * (no JPA {@code @ManyToOne}) to keep writes simple and avoid lazy-loading
 * overhead on the hot path. Money uses {@link BigDecimal} with scale
 * {@value #MONEY_SCALE} ({@code NUMERIC(19,2)}).
 */
@Entity
@Table(name = "transactions")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TransactionEntity {

    /** Scale used for all monetary values. */
    public static final int MONEY_SCALE = 2;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private TransactionType type;

    @Column(name = "amount", nullable = false, precision = 19, scale = MONEY_SCALE)
    private BigDecimal amount;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency", nullable = false, length = 3, columnDefinition = "char(3)")
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TransactionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_reason", length = 50)
    private FailureReason failureReason;

    @Column(name = "requested_at", nullable = false)
    private OffsetDateTime requestedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    /**
     * Factory for an approved (SUCCEEDED) transaction.
     */
    public static TransactionEntity approved(UUID id,
                                             UUID accountId,
                                             TransactionType type,
                                             BigDecimal amount,
                                             String currency,
                                             OffsetDateTime requestedAt) {
        return build(id, accountId, type, amount, currency,
                TransactionStatus.SUCCEEDED, null, requestedAt);
    }

    /**
     * Factory for a refused (FAILED) transaction, with the failure reason set.
     */
    public static TransactionEntity rejected(UUID id,
                                             UUID accountId,
                                             TransactionType type,
                                             BigDecimal amount,
                                             String currency,
                                             FailureReason failureReason,
                                             OffsetDateTime requestedAt) {
        return build(id, accountId, type, amount, currency,
                TransactionStatus.FAILED, failureReason, requestedAt);
    }

    private static TransactionEntity build(UUID id,
                                           UUID accountId,
                                           TransactionType type,
                                           BigDecimal amount,
                                           String currency,
                                           TransactionStatus status,
                                           FailureReason failureReason,
                                           OffsetDateTime requestedAt) {
        TransactionEntity transaction = new TransactionEntity();
        transaction.id = id;
        transaction.accountId = accountId;
        transaction.type = type;
        transaction.amount = normalizeScale(amount);
        transaction.currency = currency;
        transaction.status = status;
        transaction.failureReason = failureReason;
        transaction.requestedAt = requestedAt;
        transaction.createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        return transaction;
    }

    private static BigDecimal normalizeScale(BigDecimal value) {
        return value == null ? null : value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
