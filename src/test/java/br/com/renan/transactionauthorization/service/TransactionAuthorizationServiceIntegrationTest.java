package br.com.renan.transactionauthorization.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import br.com.renan.transactionauthorization.AbstractPostgresIntegrationTest;
import br.com.renan.transactionauthorization.dto.AmountRequest;
import br.com.renan.transactionauthorization.dto.TransactionRequest;
import br.com.renan.transactionauthorization.dto.TransactionResponse;
import br.com.renan.transactionauthorization.entity.AccountEntity;
import br.com.renan.transactionauthorization.enums.AccountStatus;
import br.com.renan.transactionauthorization.enums.TransactionStatus;
import br.com.renan.transactionauthorization.enums.TransactionType;
import br.com.renan.transactionauthorization.exception.TransactionConflictException;
import br.com.renan.transactionauthorization.repository.AccountRepository;
import br.com.renan.transactionauthorization.repository.TransactionRepository;

class TransactionAuthorizationServiceIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private TransactionAuthorizationService service;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    private UUID persistEnabledAccount(String balance) {
        AccountEntity account = AccountEntity.newImportedAccount(
                UUID.randomUUID(), UUID.randomUUID(), AccountStatus.ENABLED,
                OffsetDateTime.now(ZoneOffset.UTC));
        account.setBalanceAmount(new BigDecimal(balance).setScale(2));
        return accountRepository.saveAndFlush(account).getId();
    }

    private static TransactionRequest request(UUID accountId, TransactionType type, String value) {
        return new TransactionRequest(accountId, type, new AmountRequest(new BigDecimal(value), "BRL"));
    }

    private BigDecimal balanceOf(UUID accountId) {
        return accountRepository.findById(accountId).orElseThrow().getBalanceAmount();
    }

    @Test
    void creditApproved_updatesBalanceInDatabase() {
        UUID accountId = persistEnabledAccount("100.00");

        TransactionResponse response = service.authorize(
                UUID.randomUUID(), request(accountId, TransactionType.CREDIT, "23.12"));

        assertThat(response.transaction().status()).isEqualTo(TransactionStatus.SUCCEEDED);
        assertThat(balanceOf(accountId)).isEqualByComparingTo("123.12");
    }

    @Test
    void debitApproved_updatesBalanceInDatabase() {
        UUID accountId = persistEnabledAccount("100.00");

        TransactionResponse response = service.authorize(
                UUID.randomUUID(), request(accountId, TransactionType.DEBIT, "40.00"));

        assertThat(response.transaction().status()).isEqualTo(TransactionStatus.SUCCEEDED);
        assertThat(balanceOf(accountId)).isEqualByComparingTo("60.00");
    }

    @Test
    void debitRejected_keepsBalanceAndPersistsFailedTransaction() {
        UUID accountId = persistEnabledAccount("30.00");
        UUID transactionId = UUID.randomUUID();

        TransactionResponse response = service.authorize(
                transactionId, request(accountId, TransactionType.DEBIT, "50.00"));

        assertThat(response.transaction().status()).isEqualTo(TransactionStatus.FAILED);
        assertThat(balanceOf(accountId)).isEqualByComparingTo("30.00");
        assertThat(transactionRepository.findById(transactionId)).isPresent();
        assertThat(transactionRepository.findById(transactionId).orElseThrow().getStatus())
                .isEqualTo(TransactionStatus.FAILED);
    }

    @Test
    void idempotentReplay_doesNotApplyBalanceTwice() {
        UUID accountId = persistEnabledAccount("0.00");
        UUID transactionId = UUID.randomUUID();
        TransactionRequest request = request(accountId, TransactionType.CREDIT, "100.00");

        service.authorize(transactionId, request);
        TransactionResponse second = service.authorize(transactionId, request);

        assertThat(second.transaction().status()).isEqualTo(TransactionStatus.SUCCEEDED);
        assertThat(balanceOf(accountId)).isEqualByComparingTo("100.00");
        assertThat(transactionRepository.findByAccountIdOrderByCreatedAtDesc(accountId)).hasSize(1);
    }

    @Test
    void idempotencyConflict_throwsConflict() {
        UUID accountId = persistEnabledAccount("100.00");
        UUID transactionId = UUID.randomUUID();
        service.authorize(transactionId, request(accountId, TransactionType.CREDIT, "10.00"));

        assertThatThrownBy(() -> service.authorize(
                transactionId, request(accountId, TransactionType.CREDIT, "20.00")))
                .isInstanceOf(TransactionConflictException.class);
    }
}
