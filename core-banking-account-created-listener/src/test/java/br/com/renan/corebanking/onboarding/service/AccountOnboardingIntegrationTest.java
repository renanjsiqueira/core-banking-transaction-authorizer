package br.com.renan.corebanking.onboarding.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import br.com.renan.corebanking.onboarding.AbstractPostgresIntegrationTest;
import br.com.renan.corebanking.onboarding.model.Account;
import br.com.renan.corebanking.onboarding.repository.AccountRepository;
import br.com.renan.corebanking.onboarding.support.AccountCreatedMessageTestFactory;

class AccountOnboardingIntegrationTest extends AbstractPostgresIntegrationTest {
    @Autowired
    private AccountCreatedConsumerService service;

    @Autowired
    private AccountRepository accountRepository;

    @BeforeEach
    void cleanDatabase() {
        accountRepository.deleteAll();
    }

    @Test
    void importsAccountWithZeroBalanceAndBrl() {
        String id = UUID.randomUUID().toString();

        service.importAccount(AccountCreatedMessageTestFactory.withId(id));

        Account account = accountRepository.findById(UUID.fromString(id)).orElseThrow();
        assertThat(account.getBalanceAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(account.getCurrency()).isEqualTo("BRL");
    }

    @Test
    void duplicateMessageDoesNotCreateDuplicateAccount() {
        String id = UUID.randomUUID().toString();

        service.importAccount(AccountCreatedMessageTestFactory.withId(id));
        service.importAccount(AccountCreatedMessageTestFactory.withId(id));

        assertThat(accountRepository.findById(UUID.fromString(id))).isPresent();
        assertThat(accountRepository.count()).isEqualTo(1);
    }
}
