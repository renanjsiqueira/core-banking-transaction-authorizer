package br.com.renan.transactionauthorization.entity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import br.com.renan.transactionauthorization.enums.AccountStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Bank account aggregate. Holds the current balance and lifecycle status.
 *
 * <p>Money is stored as {@link BigDecimal} with scale {@value #MONEY_SCALE}
 * (mapped to {@code NUMERIC(19,2)}); {@code double} is never used for money.
 * The {@link Version} field enables optimistic locking for non-critical
 * read-modify-write paths (the critical authorization path will rely on a
 * pessimistic lock, added in a later phase).
 */
@Entity
@Table(name = "accounts")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountEntity {

    /** Scale used for all monetary values. */
    public static final int MONEY_SCALE = 2;

    /** Default currency for newly imported accounts (ISO-4217). */
    public static final String DEFAULT_CURRENCY = "BRL";

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private AccountStatus status;

    @Column(name = "balance_amount", nullable = false, precision = 19, scale = MONEY_SCALE)
    private BigDecimal balanceAmount;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency", nullable = false, length = 3, columnDefinition = "char(3)")
    private String currency;

    @Column(name = "source_created_at", nullable = false)
    private OffsetDateTime sourceCreatedAt;

    @Column(name = "imported_at", nullable = false)
    private OffsetDateTime importedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /**
     * Factory for an account imported from the account-opening stream. The
     * balance starts at zero (scale {@value #MONEY_SCALE}) in {@code BRL}.
     *
     * @param id              account identifier
     * @param ownerId         account owner identifier
     * @param status          status reported by the opening system
     * @param sourceCreatedAt creation timestamp reported by the opening system
     */
    public static AccountEntity newImportedAccount(UUID id,
                                                   UUID ownerId,
                                                   AccountStatus status,
                                                   OffsetDateTime sourceCreatedAt) {
        AccountEntity account = new AccountEntity();
        account.id = id;
        account.ownerId = ownerId;
        account.status = status;
        account.balanceAmount = BigDecimal.ZERO.setScale(MONEY_SCALE);
        account.currency = DEFAULT_CURRENCY;
        account.sourceCreatedAt = sourceCreatedAt;
        account.importedAt = OffsetDateTime.now(ZoneOffset.UTC);
        return account;
    }
}
