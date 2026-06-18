package br.com.renan.transactionauthorization.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import br.com.renan.transactionauthorization.AbstractPostgresIntegrationTest;
import br.com.renan.transactionauthorization.entity.AccountEntity;
import br.com.renan.transactionauthorization.enums.AccountStatus;

class AccountRepositoryIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private AccountRepository accountRepository;

    private static AccountEntity enabledAccount() {
        return AccountEntity.newImportedAccount(
                UUID.randomUUID(), UUID.randomUUID(), AccountStatus.ENABLED,
                OffsetDateTime.now(ZoneOffset.UTC));
    }

    @Test
    void savesAndReadsAccountWithImportDefaults() {
        AccountEntity saved = accountRepository.save(enabledAccount());

        Optional<AccountEntity> found = accountRepository.findById(saved.getId());

        assertThat(found).isPresent();
        AccountEntity account = found.get();
        assertThat(account.getBalanceAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(account.getCurrency()).isEqualTo("BRL");
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ENABLED);
        assertThat(account.getVersion()).isZero();
    }

    @Test
    @Transactional
    void findByIdForUpdate_returnsLockedAccount() {
        AccountEntity saved = accountRepository.saveAndFlush(enabledAccount());

        Optional<AccountEntity> found = accountRepository.findByIdForUpdate(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
    }

    @Test
    void updatingBalance_incrementsVersion() {
        AccountEntity saved = accountRepository.save(enabledAccount());
        assertThat(saved.getVersion()).isZero();

        saved.setBalanceAmount(new BigDecimal("100.00"));
        AccountEntity updated = accountRepository.saveAndFlush(saved);

        assertThat(updated.getBalanceAmount()).isEqualByComparingTo("100.00");
        assertThat(updated.getVersion()).isEqualTo(1L);
    }
}
