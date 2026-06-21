package br.com.renan.corebanking.authorization.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import br.com.renan.corebanking.authorization.AbstractPostgresIntegrationTest;
import br.com.renan.corebanking.authorization.dto.response.TransactionResponseDTO;
import br.com.renan.corebanking.authorization.exception.TransactionConflictException;
import br.com.renan.corebanking.authorization.repository.AccountRepository;
import br.com.renan.corebanking.authorization.repository.TransactionRepository;
import br.com.renan.corebanking.authorization.support.AccountTestFactory;
import br.com.renan.corebanking.authorization.support.TransactionRequestTestFactory;
import br.com.renan.corebanking.domain.shared.enums.TransactionStatus;

class TransactionAuthorizationServiceIntegrationTest extends AbstractPostgresIntegrationTest {
    @Autowired
    private TransactionAuthorizationService service;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    private UUID persistEnabledAccount(String balance) {
        return accountRepository.saveAndFlush(AccountTestFactory.enabled(balance)).getId();
    }

    private BigDecimal balanceOf(UUID accountId) {
        return accountRepository.findById(accountId).orElseThrow().getBalanceAmount();
    }

    @Test
    void creditApprovedUpdatesBalanceInDatabase() {
        UUID accountId = persistEnabledAccount("100.00");

        TransactionResponseDTO response = service.authorize(
                UUID.randomUUID(), TransactionRequestTestFactory.credit(accountId, "23.12"));

        assertThat(response.transaction().status()).isEqualTo(TransactionStatus.SUCCEEDED);
        assertThat(balanceOf(accountId)).isEqualByComparingTo("123.12");
    }

    @Test
    void debitApprovedUpdatesBalanceInDatabase() {
        UUID accountId = persistEnabledAccount("100.00");

        TransactionResponseDTO response = service.authorize(
                UUID.randomUUID(), TransactionRequestTestFactory.debit(accountId, "40.00"));

        assertThat(response.transaction().status()).isEqualTo(TransactionStatus.SUCCEEDED);
        assertThat(balanceOf(accountId)).isEqualByComparingTo("60.00");
    }

    @Test
    void debitRejectedKeepsBalanceAndPersistsFailedTransaction() {
        UUID accountId = persistEnabledAccount("30.00");
        UUID transactionId = UUID.randomUUID();

        TransactionResponseDTO response = service.authorize(
                transactionId, TransactionRequestTestFactory.debit(accountId, "50.00"));

        assertThat(response.transaction().status()).isEqualTo(TransactionStatus.FAILED);
        assertThat(balanceOf(accountId)).isEqualByComparingTo("30.00");
        assertThat(transactionRepository.findById(transactionId).orElseThrow().getStatus())
                .isEqualTo(TransactionStatus.FAILED);
    }

    @Test
    void idempotentReplayDoesNotApplyBalanceTwice() {
        UUID accountId = persistEnabledAccount("0.00");
        UUID transactionId = UUID.randomUUID();

        service.authorize(transactionId, TransactionRequestTestFactory.credit(accountId, "100.00"));
        TransactionResponseDTO second =
                service.authorize(transactionId, TransactionRequestTestFactory.credit(accountId, "100.00"));

        assertThat(second.transaction().status()).isEqualTo(TransactionStatus.SUCCEEDED);
        assertThat(balanceOf(accountId)).isEqualByComparingTo("100.00");
        assertThat(transactionRepository.findByAccountIdOrderByCreatedAtDesc(accountId)).hasSize(1);
    }

    @Test
    void idempotentReplayReturnsOriginalResultingBalanceAfterAccountChanges() {
        UUID accountId = persistEnabledAccount("0.00");
        UUID originalTransactionId = UUID.randomUUID();

        TransactionResponseDTO first = service.authorize(
                originalTransactionId, TransactionRequestTestFactory.credit(accountId, "100.00"));
        service.authorize(UUID.randomUUID(), TransactionRequestTestFactory.credit(accountId, "50.00"));
        TransactionResponseDTO replay = service.authorize(
                originalTransactionId, TransactionRequestTestFactory.credit(accountId, "100.00"));

        assertThat(first.account().balance().amount()).isEqualByComparingTo("100.00");
        assertThat(replay.account().balance().amount()).isEqualByComparingTo("100.00");
        assertThat(balanceOf(accountId)).isEqualByComparingTo("150.00");
    }

    @Test
    void idempotencyConflictThrowsAndDoesNotChangeBalance() {
        UUID accountId = persistEnabledAccount("100.00");
        UUID transactionId = UUID.randomUUID();
        service.authorize(transactionId, TransactionRequestTestFactory.credit(accountId, "10.00"));

        assertThatThrownBy(() -> service.authorize(
                transactionId, TransactionRequestTestFactory.credit(accountId, "20.00")))
                .isInstanceOf(TransactionConflictException.class);

        assertThat(balanceOf(accountId)).isEqualByComparingTo("110.00");
    }

    @Test
    void transactionsOnDifferentAccountsAreIndependent() {
        UUID first = persistEnabledAccount("100.00");
        UUID second = persistEnabledAccount("100.00");

        service.authorize(UUID.randomUUID(), TransactionRequestTestFactory.credit(first, "50.00"));
        service.authorize(UUID.randomUUID(), TransactionRequestTestFactory.debit(second, "30.00"));

        assertThat(balanceOf(first)).isEqualByComparingTo("150.00");
        assertThat(balanceOf(second)).isEqualByComparingTo("70.00");
    }
}
