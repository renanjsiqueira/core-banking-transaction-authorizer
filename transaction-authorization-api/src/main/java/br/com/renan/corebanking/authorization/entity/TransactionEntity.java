package br.com.renan.corebanking.authorization.entity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import br.com.renan.corebanking.shared.enums.FailureReason;
import br.com.renan.corebanking.shared.enums.TransactionStatus;
import br.com.renan.corebanking.shared.enums.TransactionType;
import br.com.renan.corebanking.shared.money.MoneyConstants;
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

@Entity
@Table(name = "transactions")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TransactionEntity {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private TransactionType type;

    @Column(name = "amount", nullable = false, precision = 19, scale = MoneyConstants.SCALE)
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

    public static TransactionEntity approved(UUID id,
                                             UUID accountId,
                                             TransactionType type,
                                             BigDecimal amount,
                                             String currency,
                                             OffsetDateTime requestedAt) {
        return build(id, accountId, type, amount, currency,
                TransactionStatus.SUCCEEDED, null, requestedAt);
    }

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
        transaction.amount = MoneyConstants.normalize(amount);
        transaction.currency = currency;
        transaction.status = status;
        transaction.failureReason = failureReason;
        transaction.requestedAt = requestedAt;
        transaction.createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        return transaction;
    }
}
