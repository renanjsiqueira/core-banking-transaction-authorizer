package br.com.renan.transactionauthorization.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import br.com.renan.transactionauthorization.enums.AccountStatus;

class AccountEntityTest {

    @Test
    void newImportedAccount_startsWithZeroBalanceScaleTwo() {
        AccountEntity account = AccountEntity.newImportedAccount(
                UUID.randomUUID(), UUID.randomUUID(), AccountStatus.ENABLED,
                OffsetDateTime.now(ZoneOffset.UTC));

        assertThat(account.getBalanceAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(account.getBalanceAmount().scale()).isEqualTo(2);
    }

    @Test
    void newImportedAccount_startsWithBrlCurrency() {
        AccountEntity account = AccountEntity.newImportedAccount(
                UUID.randomUUID(), UUID.randomUUID(), AccountStatus.ENABLED,
                OffsetDateTime.now(ZoneOffset.UTC));

        assertThat(account.getCurrency()).isEqualTo("BRL");
    }

    @Test
    void newImportedAccount_copiesIdentifiersStatusAndSourceTimestamp() {
        UUID id = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        OffsetDateTime sourceCreatedAt = OffsetDateTime.now(ZoneOffset.UTC).minusDays(1);

        AccountEntity account = AccountEntity.newImportedAccount(
                id, ownerId, AccountStatus.BLOCKED, sourceCreatedAt);

        assertThat(account.getId()).isEqualTo(id);
        assertThat(account.getOwnerId()).isEqualTo(ownerId);
        assertThat(account.getStatus()).isEqualTo(AccountStatus.BLOCKED);
        assertThat(account.getSourceCreatedAt()).isEqualTo(sourceCreatedAt);
        assertThat(account.getImportedAt()).isNotNull();
    }
}
