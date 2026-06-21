package br.com.renan.corebanking.authorization.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import br.com.renan.corebanking.authorization.config.RedisLockProperties;
import br.com.renan.corebanking.authorization.dto.request.TransactionRequestDTO;
import br.com.renan.corebanking.authorization.dto.response.TransactionResponseDTO;
import br.com.renan.corebanking.authorization.exception.AccountNotFoundException;
import br.com.renan.corebanking.authorization.exception.InvalidTransactionRequestException;
import br.com.renan.corebanking.authorization.exception.LockUnavailableException;
import br.com.renan.corebanking.authorization.exception.ResourceLockedException;
import br.com.renan.corebanking.authorization.exception.TransactionConflictException;
import br.com.renan.corebanking.authorization.exception.UnsupportedCurrencyException;
import br.com.renan.corebanking.authorization.mapper.TransactionResponseMapper;
import br.com.renan.corebanking.authorization.model.Account;
import br.com.renan.corebanking.authorization.model.Transaction;
import br.com.renan.corebanking.authorization.observability.TransactionAuthorizationMetrics;
import br.com.renan.corebanking.authorization.repository.AccountRepository;
import br.com.renan.corebanking.authorization.repository.TransactionRepository;
import br.com.renan.corebanking.domain.shared.enums.AccountStatus;
import br.com.renan.corebanking.domain.shared.enums.FailureReason;
import br.com.renan.corebanking.domain.shared.enums.TransactionStatus;
import br.com.renan.corebanking.domain.shared.enums.TransactionType;
import br.com.renan.corebanking.domain.shared.money.MoneyConstants;

@Service
public class TransactionAuthorizationServiceImpl implements TransactionAuthorizationService {
    private static final Logger log = LoggerFactory.getLogger(TransactionAuthorizationServiceImpl.class);

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionResponseMapper responseMapper;
    private final RedisDistributedLockService lockService;
    private final RedisLockProperties lockProperties;
    private final TransactionOperations transactionOperations;
    private final TransactionAuthorizationMetrics metrics;

    public TransactionAuthorizationServiceImpl(AccountRepository accountRepository,
                                               TransactionRepository transactionRepository,
                                               TransactionResponseMapper responseMapper,
                                               RedisDistributedLockService lockService,
                                               RedisLockProperties lockProperties,
                                               TransactionOperations transactionOperations,
                                               TransactionAuthorizationMetrics metrics) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.responseMapper = responseMapper;
        this.lockService = lockService;
        this.lockProperties = lockProperties;
        this.transactionOperations = transactionOperations;
        this.metrics = metrics;
    }

    @Override
    public TransactionResponseDTO authorize(UUID transactionId, TransactionRequestDTO request) {
        long startedAtNanos = System.nanoTime();
        try {
            validateCurrency(request.amount().currency());
            BigDecimal amount = validateAmount(request.amount().value());

            if (!lockProperties.isEnabled()) {
                return authorizeInTransaction(transactionId, request, amount, startedAtNanos);
            }

            String transactionKey = "lock:transaction:" + transactionId;
            String accountKey = "lock:account:" + request.accountId();

            return lockService.executeWithLock(transactionKey, lockProperties.getTransactionLockTtl(),
                    lockProperties.getWaitTimeout(), lockProperties.getRetryDelay(),
                    lockProperties.getMaxRetryDelay(),
                    () -> lockService.executeWithLock(accountKey, lockProperties.getAccountLockTtl(),
                            lockProperties.getWaitTimeout(), lockProperties.getRetryDelay(),
                            lockProperties.getMaxRetryDelay(),
                            () -> authorizeInTransaction(transactionId, request, amount, startedAtNanos)));
        } catch (UnsupportedCurrencyException ex) {
            logAuthorizationDecision(transactionId, request.accountId(), request.type(),
                    TransactionStatus.FAILED, FailureReason.UNSUPPORTED_CURRENCY.name(), startedAtNanos);
            throw ex;
        } catch (InvalidTransactionRequestException ex) {
            logAuthorizationDecision(transactionId, request.accountId(), request.type(),
                    TransactionStatus.FAILED, FailureReason.INVALID_AMOUNT.name(), startedAtNanos);
            throw ex;
        } catch (ResourceLockedException ex) {
            logAuthorizationDecision(transactionId, request.accountId(), request.type(),
                    TransactionStatus.FAILED, "LOCK_TIMEOUT", startedAtNanos);
            throw ex;
        } catch (LockUnavailableException ex) {
            logAuthorizationDecision(transactionId, request.accountId(), request.type(),
                    TransactionStatus.FAILED, "LOCK_UNAVAILABLE", startedAtNanos);
            throw ex;
        }
    }

    private TransactionResponseDTO authorizeInTransaction(UUID transactionId,
                                                          TransactionRequestDTO request,
                                                          BigDecimal amount,
                                                          long startedAtNanos) {
        return transactionOperations.execute(status -> doAuthorize(transactionId, request, amount, startedAtNanos));
    }

    private TransactionResponseDTO doAuthorize(UUID transactionId,
                                               TransactionRequestDTO request,
                                               BigDecimal amount,
                                               long startedAtNanos) {
        Optional<Account> accountResult = accountRepository.findByIdForUpdate(request.accountId());
        if (accountResult.isEmpty()) {
            metrics.recordAccountNotFound(request.type());
            logAuthorizationDecision(transactionId, request.accountId(), request.type(),
                    TransactionStatus.FAILED, FailureReason.ACCOUNT_NOT_FOUND.name(), startedAtNanos);
            throw new AccountNotFoundException(request.accountId());
        }
        Account account = accountResult.get();

        Optional<Transaction> existing = findExistingTransaction(transactionId);
        if (existing.isPresent()) {
            return handleReplay(transactionId, request, existing.get(), account, startedAtNanos);
        }

        if (account.getStatus() != AccountStatus.ENABLED) {
            Transaction refused = buildFailedTransaction(
                    transactionId, request, amount, account.getBalanceAmount(), FailureReason.ACCOUNT_DISABLED);
            Transaction saved = transactionRepository.save(refused);
            metrics.recordAuthorization(saved);
            logAuthorizationDecision(saved, startedAtNanos);
            return buildResponse(saved, account);
        }

        Transaction transaction = switch (request.type()) {
            case CREDIT -> authorizeCredit(transactionId, request, account, amount);
            case DEBIT -> authorizeDebit(transactionId, request, account, amount);
        };
        metrics.recordAuthorization(transaction);
        logAuthorizationDecision(transaction, startedAtNanos);
        return buildResponse(transaction, account);
    }

    private TransactionResponseDTO handleReplay(UUID transactionId,
                                                TransactionRequestDTO request,
                                                Transaction existing,
                                                Account lockedAccount,
                                                long startedAtNanos) {
        if (!isSameLogicalPayload(existing, request)) {
            metrics.recordIdempotencyConflict(request.type());
            logAuthorizationDecision(transactionId, request.accountId(), request.type(),
                    TransactionStatus.FAILED, FailureReason.IDEMPOTENCY_CONFLICT.name(), startedAtNanos);
            throw new TransactionConflictException(
                    "transactionId " + transactionId + " already used with a different payload");
        }
        metrics.recordIdempotentReplay(existing);
        logAuthorizationDecision(existing, startedAtNanos);
        return buildResponse(existing, lockedAccount);
    }

    private void validateCurrency(String currency) {
        if (!MoneyConstants.DEFAULT_CURRENCY.equals(currency)) {
            throw new UnsupportedCurrencyException(currency);
        }
    }

    private BigDecimal validateAmount(BigDecimal value) {
        if (value == null || value.signum() <= 0) {
            throw new InvalidTransactionRequestException("amount.value must be greater than zero");
        }
        return MoneyConstants.normalize(value);
    }

    private Optional<Transaction> findExistingTransaction(UUID transactionId) {
        return transactionRepository.findById(transactionId);
    }

    private boolean isSameLogicalPayload(Transaction transaction, TransactionRequestDTO request) {
        return transaction.getAccountId().equals(request.accountId())
                && transaction.getType() == request.type()
                && transaction.getAmount().compareTo(MoneyConstants.normalize(request.amount().value())) == 0
                && transaction.getCurrency().equals(request.amount().currency());
    }

    private Transaction authorizeCredit(UUID transactionId,
                                        TransactionRequestDTO request,
                                        Account account,
                                        BigDecimal amount) {
        BigDecimal newBalance = MoneyConstants.normalize(account.getBalanceAmount().add(amount));
        account.setBalanceAmount(newBalance);
        accountRepository.save(account);
        return transactionRepository.save(buildSucceededTransaction(transactionId, request, amount, newBalance));
    }

    private Transaction authorizeDebit(UUID transactionId,
                                       TransactionRequestDTO request,
                                       Account account,
                                       BigDecimal amount) {
        BigDecimal newBalance = MoneyConstants.normalize(account.getBalanceAmount().subtract(amount));
        if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
            Transaction refused = buildFailedTransaction(
                    transactionId, request, amount, account.getBalanceAmount(), FailureReason.INSUFFICIENT_FUNDS);
            return transactionRepository.save(refused);
        }
        account.setBalanceAmount(newBalance);
        accountRepository.save(account);
        return transactionRepository.save(buildSucceededTransaction(transactionId, request, amount, newBalance));
    }

    private Transaction buildSucceededTransaction(UUID transactionId,
                                                  TransactionRequestDTO request,
                                                  BigDecimal amount,
                                                  BigDecimal resultingBalanceAmount) {
        return Transaction.approved(
                transactionId, request.accountId(), request.type(),
                amount, resultingBalanceAmount, request.amount().currency(), now());
    }

    private Transaction buildFailedTransaction(UUID transactionId,
                                               TransactionRequestDTO request,
                                               BigDecimal amount,
                                               BigDecimal resultingBalanceAmount,
                                               FailureReason reason) {
        return Transaction.rejected(
                transactionId, request.accountId(), request.type(),
                amount, resultingBalanceAmount, request.amount().currency(), reason, now());
    }

    private TransactionResponseDTO buildResponse(Transaction transaction, Account account) {
        return responseMapper.toResponse(transaction, account);
    }

    private void logAuthorizationDecision(Transaction transaction, long startedAtNanos) {
        String failureReason = transaction.getFailureReason() == null
                ? "NONE"
                : transaction.getFailureReason().name();
        logAuthorizationDecision(transaction.getId(), transaction.getAccountId(), transaction.getType(),
                transaction.getStatus(), failureReason, startedAtNanos);
    }

    private void logAuthorizationDecision(UUID transactionId,
                                          UUID accountId,
                                          TransactionType type,
                                          TransactionStatus status,
                                          String failureReason,
                                          long startedAtNanos) {
        log.info("event=authorization_decision transactionId={} accountId={} type={} status={} failureReason={} latencyMs={}",
                transactionId, accountId, type, status, failureReason, elapsedMillis(startedAtNanos));
    }

    private static long elapsedMillis(long startedAtNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos);
    }

    private static OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
