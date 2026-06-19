package br.com.renan.corebanking.onboarding.entity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import br.com.renan.corebanking.shared.enums.AccountStatus;
import br.com.renan.corebanking.shared.money.MoneyConstants;
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

@Entity
@Table(name = "accounts")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountEntity {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private AccountStatus status;

    @Column(name = "balance_amount", nullable = false, precision = 19, scale = MoneyConstants.SCALE)
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

    public static AccountEntity newImportedAccount(UUID id,
                                                   UUID ownerId,
                                                   AccountStatus status,
                                                   OffsetDateTime sourceCreatedAt) {
        AccountEntity account = new AccountEntity();
        account.id = id;
        account.ownerId = ownerId;
        account.status = status;
        account.balanceAmount = MoneyConstants.zero();
        account.currency = MoneyConstants.DEFAULT_CURRENCY;
        account.sourceCreatedAt = sourceCreatedAt;
        account.importedAt = OffsetDateTime.now(ZoneOffset.UTC);
        return account;
    }
}
