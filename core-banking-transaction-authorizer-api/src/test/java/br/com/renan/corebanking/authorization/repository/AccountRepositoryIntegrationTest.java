package br.com.renan.corebanking.authorization.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import br.com.renan.corebanking.authorization.AbstractPostgresIntegrationTest;
import br.com.renan.corebanking.authorization.model.Account;
import br.com.renan.corebanking.domain.shared.enums.AccountStatus;

class AccountRepositoryIntegrationTest extends AbstractPostgresIntegrationTest {
    @Autowired
    private AccountRepository accountRepository;

    private static Account enabledAccount() {
        return Account.newImportedAccount(
                UUID.randomUUID(), UUID.randomUUID(), AccountStatus.ENABLED,
                OffsetDateTime.now(ZoneOffset.UTC));
    }

    @Test
    void savesAndReadsAccountWithImportDefaults() {
        Account saved = accountRepository.save(enabledAccount());

        Optional<Account> found = accountRepository.findById(saved.getId());

        assertThat(found).isPresent();
        Account account = found.get();
        assertThat(account.getBalanceAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(account.getCurrency()).isEqualTo("BRL");
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ENABLED);
        assertThat(account.getVersion()).isZero();
    }

    @Test
    @Transactional
    void findByIdForUpdateReturnsLockedAccount() {
        Account saved = accountRepository.saveAndFlush(enabledAccount());

        Optional<Account> found = accountRepository.findByIdForUpdate(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
    }

    @Test
    void updatingBalanceIncrementsVersion() {
        Account saved = accountRepository.save(enabledAccount());
        assertThat(saved.getVersion()).isZero();

        saved.setBalanceAmount(new BigDecimal("100.00"));
        Account updated = accountRepository.saveAndFlush(saved);

        assertThat(updated.getBalanceAmount()).isEqualByComparingTo("100.00");
        assertThat(updated.getVersion()).isEqualTo(1L);
    }
}
