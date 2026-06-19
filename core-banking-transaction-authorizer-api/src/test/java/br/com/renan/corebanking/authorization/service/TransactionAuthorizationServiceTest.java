package br.com.renan.corebanking.authorization.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import br.com.renan.corebanking.authorization.config.RedisLockProperties;
import br.com.renan.corebanking.authorization.dto.response.TransactionResponseDTO;
import br.com.renan.corebanking.authorization.model.Account;
import br.com.renan.corebanking.authorization.model.Transaction;
import br.com.renan.corebanking.authorization.exception.AccountNotFoundException;
import br.com.renan.corebanking.authorization.exception.InvalidTransactionRequestException;
import br.com.renan.corebanking.authorization.exception.ResourceLockedException;
import br.com.renan.corebanking.authorization.exception.TransactionConflictException;
import br.com.renan.corebanking.authorization.exception.UnsupportedCurrencyException;
import br.com.renan.corebanking.authorization.mapper.TransactionResponseMapper;
import br.com.renan.corebanking.authorization.repository.AccountRepository;
import br.com.renan.corebanking.authorization.repository.TransactionRepository;
import br.com.renan.corebanking.authorization.support.AccountTestFactory;
import br.com.renan.corebanking.authorization.support.TransactionRequestTestFactory;
import br.com.renan.corebanking.authorization.support.TransactionTestFactory;
import br.com.renan.corebanking.domain.shared.enums.AccountStatus;
import br.com.renan.corebanking.domain.shared.enums.TransactionStatus;
import br.com.renan.corebanking.domain.shared.enums.TransactionType;

class TransactionAuthorizationServiceTest {
    private AccountRepository accountRepository;
    private TransactionRepository transactionRepository;
    private RedisDistributedLockService lockService;
    private TransactionOperations transactionOperations;
    private TransactionAuthorizationServiceImpl service;

    @BeforeEach
    void setUp() {
        accountRepository = mock(AccountRepository.class);
        transactionRepository = mock(TransactionRepository.class);
        lockService = mock(RedisDistributedLockService.class);
        transactionOperations = mock(TransactionOperations.class);
        service = new TransactionAuthorizationServiceImpl(
                accountRepository, transactionRepository, new TransactionResponseMapper(),
                lockService, new RedisLockProperties(), transactionOperations);

        when(lockService.executeWithLock(any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(4)).get());
        when(transactionOperations.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(new SimpleTransactionStatus());
        });

        when(transactionRepository.findById(any(UUID.class))).thenReturn(Optional.empty());
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(accountRepository.save(any(Account.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void givenAccount(Account account) {
        when(accountRepository.findByIdForUpdate(account.getId())).thenReturn(Optional.of(account));
    }

    @Test
    void creditApprovedAddsBalance() {
        Account account = AccountTestFactory.enabled("100.00");
        givenAccount(account);

        TransactionResponseDTO response = service.authorize(
                UUID.randomUUID(), TransactionRequestTestFactory.credit(account.getId(), "50.00"));

        assertThat(response.transaction().status()).isEqualTo(TransactionStatus.SUCCEEDED);
        assertThat(account.getBalanceAmount()).isEqualByComparingTo("150.00");
        assertThat(response.account().balance().amount()).isEqualByComparingTo("150.00");
        verify(accountRepository).save(account);

        verify(lockService).executeWithLock(startsWith("lock:transaction:"), any(), any(), any(), any());
        verify(lockService).executeWithLock(startsWith("lock:account:"), any(), any(), any(), any());
        verify(accountRepository).findByIdForUpdate(account.getId());
    }

    @Test
    void whenTransactionLockFailsDoesNotProcess() {
        UUID accountId = UUID.randomUUID();
        doThrow(new ResourceLockedException("lock:transaction"))
                .when(lockService).executeWithLock(startsWith("lock:transaction:"), any(), any(), any(), any());

        assertThatThrownBy(() -> service.authorize(
                UUID.randomUUID(), TransactionRequestTestFactory.credit(accountId, "10.00")))
                .isInstanceOf(ResourceLockedException.class);

        verify(transactionOperations, never()).execute(any());
        verify(accountRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void whenAccountLockFailsDoesNotProcess() {
        UUID accountId = UUID.randomUUID();
        doThrow(new ResourceLockedException("lock:account"))
                .when(lockService).executeWithLock(startsWith("lock:account:"), any(), any(), any(), any());

        assertThatThrownBy(() -> service.authorize(
                UUID.randomUUID(), TransactionRequestTestFactory.credit(accountId, "10.00")))
                .isInstanceOf(ResourceLockedException.class);

        verify(transactionOperations, never()).execute(any());
        verify(accountRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void debitApprovedSubtractsBalance() {
        Account account = AccountTestFactory.enabled("100.00");
        givenAccount(account);

        TransactionResponseDTO response = service.authorize(
                UUID.randomUUID(), TransactionRequestTestFactory.debit(account.getId(), "30.00"));

        assertThat(response.transaction().status()).isEqualTo(TransactionStatus.SUCCEEDED);
        assertThat(account.getBalanceAmount()).isEqualByComparingTo("70.00");
        verify(accountRepository).save(account);
    }

    @Test
    void debitInsufficientFundsSavesFailedTransaction() {
        Account account = AccountTestFactory.enabled("20.00");
        givenAccount(account);

        TransactionResponseDTO response = service.authorize(
                UUID.randomUUID(), TransactionRequestTestFactory.debit(account.getId(), "50.00"));

        assertThat(response.transaction().status()).isEqualTo(TransactionStatus.FAILED);
        verify(transactionRepository).save(any(Transaction.class));
    }

    @Test
    void debitInsufficientFundsDoesNotChangeBalance() {
        Account account = AccountTestFactory.enabled("20.00");
        givenAccount(account);

        service.authorize(UUID.randomUUID(), TransactionRequestTestFactory.debit(account.getId(), "50.00"));

        assertThat(account.getBalanceAmount()).isEqualByComparingTo("20.00");
        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    void accountNotFoundThrows() {
        UUID accountId = UUID.randomUUID();
        when(accountRepository.findByIdForUpdate(accountId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.authorize(
                UUID.randomUUID(), TransactionRequestTestFactory.credit(accountId, "10.00")))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void disabledAccountSavesFailedTransactionWithoutBalanceChange() {
        Account account = AccountTestFactory.account(UUID.randomUUID(), AccountStatus.BLOCKED, "100.00");
        givenAccount(account);

        TransactionResponseDTO response = service.authorize(
                UUID.randomUUID(), TransactionRequestTestFactory.credit(account.getId(), "10.00"));

        assertThat(response.transaction().status()).isEqualTo(TransactionStatus.FAILED);
        assertThat(account.getBalanceAmount()).isEqualByComparingTo("100.00");
        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    void unsupportedCurrencyThrows() {
        UUID accountId = UUID.randomUUID();

        assertThatThrownBy(() -> service.authorize(
                UUID.randomUUID(),
                TransactionRequestTestFactory.of(accountId, TransactionType.CREDIT, "10.00", "USD")))
                .isInstanceOf(UnsupportedCurrencyException.class);
    }

    @Test
    void zeroAmountThrows() {
        UUID accountId = UUID.randomUUID();

        assertThatThrownBy(() -> service.authorize(
                UUID.randomUUID(), TransactionRequestTestFactory.credit(accountId, "0.00")))
                .isInstanceOf(InvalidTransactionRequestException.class);
    }

    @Test
    void negativeAmountThrows() {
        UUID accountId = UUID.randomUUID();

        assertThatThrownBy(() -> service.authorize(
                UUID.randomUUID(), TransactionRequestTestFactory.credit(accountId, "-10.00")))
                .isInstanceOf(InvalidTransactionRequestException.class);
    }

    @Test
    void idempotentReplaySamePayloadReturnsStoredResultWithoutReapplyingBalance() {
        Account account = AccountTestFactory.enabled("150.00");
        givenAccount(account);
        Transaction stored = TransactionTestFactory.approved(
                UUID.randomUUID(), account.getId(), TransactionType.CREDIT, "50.00");
        when(transactionRepository.findById(stored.getId())).thenReturn(Optional.of(stored));

        TransactionResponseDTO response = service.authorize(
                stored.getId(), TransactionRequestTestFactory.credit(account.getId(), "50.00"));

        assertThat(response.transaction().status()).isEqualTo(TransactionStatus.SUCCEEDED);
        assertThat(response.account().balance().amount()).isEqualByComparingTo("150.00");
        verify(accountRepository, never()).save(any(Account.class));
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    void idempotentReplayDifferentPayloadThrowsConflict() {
        Account account = AccountTestFactory.enabled("150.00");
        givenAccount(account);
        Transaction stored = TransactionTestFactory.approved(
                UUID.randomUUID(), account.getId(), TransactionType.CREDIT, "50.00");
        when(transactionRepository.findById(stored.getId())).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> service.authorize(
                stored.getId(), TransactionRequestTestFactory.credit(account.getId(), "999.00")))
                .isInstanceOf(TransactionConflictException.class);
    }
}
