package br.com.renan.transactionauthorization.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.com.renan.transactionauthorization.dto.AmountRequest;
import br.com.renan.transactionauthorization.dto.TransactionRequest;
import br.com.renan.transactionauthorization.dto.TransactionResponse;
import br.com.renan.transactionauthorization.entity.AccountEntity;
import br.com.renan.transactionauthorization.entity.TransactionEntity;
import br.com.renan.transactionauthorization.enums.AccountStatus;
import br.com.renan.transactionauthorization.enums.FailureReason;
import br.com.renan.transactionauthorization.enums.TransactionStatus;
import br.com.renan.transactionauthorization.enums.TransactionType;
import br.com.renan.transactionauthorization.exception.AccountNotFoundException;
import br.com.renan.transactionauthorization.exception.TransactionConflictException;
import br.com.renan.transactionauthorization.exception.UnsupportedCurrencyException;
import br.com.renan.transactionauthorization.mapper.TransactionResponseMapper;
import br.com.renan.transactionauthorization.repository.AccountRepository;
import br.com.renan.transactionauthorization.repository.TransactionRepository;

class TransactionAuthorizationServiceTest {

    private AccountRepository accountRepository;
    private TransactionRepository transactionRepository;
    private TransactionAuthorizationServiceImpl service;

    @BeforeEach
    void setUp() {
        accountRepository = mock(AccountRepository.class);
        transactionRepository = mock(TransactionRepository.class);
        service = new TransactionAuthorizationServiceImpl(
                accountRepository, transactionRepository, new TransactionResponseMapper());

        // default: no existing transaction; repositories echo saved entities
        when(transactionRepository.findById(any(UUID.class))).thenReturn(Optional.empty());
        when(transactionRepository.save(any(TransactionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(accountRepository.save(any(AccountEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static AccountEntity account(UUID id, AccountStatus status, String balance) {
        AccountEntity account = AccountEntity.newImportedAccount(
                id, UUID.randomUUID(), status, OffsetDateTime.now(ZoneOffset.UTC));
        account.setBalanceAmount(new BigDecimal(balance).setScale(2));
        return account;
    }

    private static TransactionRequest request(UUID accountId, TransactionType type, String value, String currency) {
        return new TransactionRequest(accountId, type, new AmountRequest(new BigDecimal(value), currency));
    }

    @Test
    void creditApproved_addsBalance() {
        UUID accountId = UUID.randomUUID();
        AccountEntity account = account(accountId, AccountStatus.ENABLED, "100.00");
        when(accountRepository.findByIdForUpdate(accountId)).thenReturn(Optional.of(account));

        TransactionResponse response = service.authorize(
                UUID.randomUUID(), request(accountId, TransactionType.CREDIT, "50.00", "BRL"));

        assertThat(response.transaction().status()).isEqualTo(TransactionStatus.SUCCEEDED);
        assertThat(account.getBalanceAmount()).isEqualByComparingTo("150.00");
        assertThat(response.account().balance().amount()).isEqualByComparingTo("150.00");
        verify(accountRepository).save(account);
    }

    @Test
    void debitApproved_subtractsBalance() {
        UUID accountId = UUID.randomUUID();
        AccountEntity account = account(accountId, AccountStatus.ENABLED, "100.00");
        when(accountRepository.findByIdForUpdate(accountId)).thenReturn(Optional.of(account));

        TransactionResponse response = service.authorize(
                UUID.randomUUID(), request(accountId, TransactionType.DEBIT, "30.00", "BRL"));

        assertThat(response.transaction().status()).isEqualTo(TransactionStatus.SUCCEEDED);
        assertThat(account.getBalanceAmount()).isEqualByComparingTo("70.00");
        verify(accountRepository).save(account);
    }

    @Test
    void debitInsufficientFunds_savesFailedTransaction() {
        UUID accountId = UUID.randomUUID();
        AccountEntity account = account(accountId, AccountStatus.ENABLED, "20.00");
        when(accountRepository.findByIdForUpdate(accountId)).thenReturn(Optional.of(account));

        TransactionResponse response = service.authorize(
                UUID.randomUUID(), request(accountId, TransactionType.DEBIT, "50.00", "BRL"));

        assertThat(response.transaction().status()).isEqualTo(TransactionStatus.FAILED);
        verify(transactionRepository).save(any(TransactionEntity.class));
    }

    @Test
    void debitInsufficientFunds_doesNotChangeBalance() {
        UUID accountId = UUID.randomUUID();
        AccountEntity account = account(accountId, AccountStatus.ENABLED, "20.00");
        when(accountRepository.findByIdForUpdate(accountId)).thenReturn(Optional.of(account));

        service.authorize(UUID.randomUUID(), request(accountId, TransactionType.DEBIT, "50.00", "BRL"));

        assertThat(account.getBalanceAmount()).isEqualByComparingTo("20.00");
        verify(accountRepository, never()).save(any(AccountEntity.class));
    }

    @Test
    void accountNotFound_throws() {
        UUID accountId = UUID.randomUUID();
        when(accountRepository.findByIdForUpdate(accountId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.authorize(
                UUID.randomUUID(), request(accountId, TransactionType.CREDIT, "10.00", "BRL")))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void disabledAccount_savesFailedTransactionWithoutBalanceChange() {
        UUID accountId = UUID.randomUUID();
        AccountEntity account = account(accountId, AccountStatus.BLOCKED, "100.00");
        when(accountRepository.findByIdForUpdate(accountId)).thenReturn(Optional.of(account));

        TransactionResponse response = service.authorize(
                UUID.randomUUID(), request(accountId, TransactionType.CREDIT, "10.00", "BRL"));

        assertThat(response.transaction().status()).isEqualTo(TransactionStatus.FAILED);
        assertThat(account.getBalanceAmount()).isEqualByComparingTo("100.00");
        verify(accountRepository, never()).save(any(AccountEntity.class));
    }

    @Test
    void unsupportedCurrency_throws() {
        UUID accountId = UUID.randomUUID();

        assertThatThrownBy(() -> service.authorize(
                UUID.randomUUID(), request(accountId, TransactionType.CREDIT, "10.00", "USD")))
                .isInstanceOf(UnsupportedCurrencyException.class);
    }

    @Test
    void idempotentReplay_sameSamePayload_returnsStoredResult() {
        UUID accountId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        AccountEntity account = account(accountId, AccountStatus.ENABLED, "150.00");
        TransactionEntity stored = TransactionEntity.approved(
                transactionId, accountId, TransactionType.CREDIT,
                new BigDecimal("50.00"), "BRL", OffsetDateTime.now(ZoneOffset.UTC));
        when(transactionRepository.findById(transactionId)).thenReturn(Optional.of(stored));
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

        TransactionResponse response = service.authorize(
                transactionId, request(accountId, TransactionType.CREDIT, "50.00", "BRL"));

        assertThat(response.transaction().status()).isEqualTo(TransactionStatus.SUCCEEDED);
        verify(accountRepository, never()).findByIdForUpdate(any());
        verify(accountRepository, never()).save(any());
    }

    @Test
    void idempotentReplay_differentPayload_throwsConflict() {
        UUID accountId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        TransactionEntity stored = TransactionEntity.approved(
                transactionId, accountId, TransactionType.CREDIT,
                new BigDecimal("50.00"), "BRL", OffsetDateTime.now(ZoneOffset.UTC));
        when(transactionRepository.findById(transactionId)).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> service.authorize(
                transactionId, request(accountId, TransactionType.CREDIT, "999.00", "BRL")))
                .isInstanceOf(TransactionConflictException.class);
    }
}
