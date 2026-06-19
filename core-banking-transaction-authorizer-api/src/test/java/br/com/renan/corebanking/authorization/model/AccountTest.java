package br.com.renan.corebanking.authorization.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import br.com.renan.corebanking.domain.shared.enums.AccountStatus;

class AccountTest {
    @Test
    void newImportedAccountStartsWithZeroBalanceScaleTwo() {
        Account account = Account.newImportedAccount(
                UUID.randomUUID(), UUID.randomUUID(), AccountStatus.ENABLED,
                OffsetDateTime.now(ZoneOffset.UTC));

        assertThat(account.getBalanceAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(account.getBalanceAmount().scale()).isEqualTo(2);
    }

    @Test
    void newImportedAccountStartsWithBrlCurrency() {
        Account account = Account.newImportedAccount(
                UUID.randomUUID(), UUID.randomUUID(), AccountStatus.ENABLED,
                OffsetDateTime.now(ZoneOffset.UTC));

        assertThat(account.getCurrency()).isEqualTo("BRL");
    }

    @Test
    void newImportedAccountCopiesIdentifiersStatusAndSourceTimestamp() {
        UUID id = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        OffsetDateTime sourceCreatedAt = OffsetDateTime.now(ZoneOffset.UTC).minusDays(1);

        Account account = Account.newImportedAccount(
                id, ownerId, AccountStatus.BLOCKED, sourceCreatedAt);

        assertThat(account.getId()).isEqualTo(id);
        assertThat(account.getOwnerId()).isEqualTo(ownerId);
        assertThat(account.getStatus()).isEqualTo(AccountStatus.BLOCKED);
        assertThat(account.getSourceCreatedAt()).isEqualTo(sourceCreatedAt);
        assertThat(account.getImportedAt()).isNotNull();
    }
}
