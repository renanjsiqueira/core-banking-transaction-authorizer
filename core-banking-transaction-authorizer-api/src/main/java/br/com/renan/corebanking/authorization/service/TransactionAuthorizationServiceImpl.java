package br.com.renan.corebanking.authorization.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import br.com.renan.corebanking.authorization.config.RedisLockProperties;
import br.com.renan.corebanking.authorization.dto.request.TransactionRequestDTO;
import br.com.renan.corebanking.authorization.dto.response.TransactionResponseDTO;
import br.com.renan.corebanking.authorization.model.Account;
import br.com.renan.corebanking.authorization.model.Transaction;
import br.com.renan.corebanking.authorization.exception.AccountNotFoundException;
import br.com.renan.corebanking.authorization.exception.InvalidTransactionRequestException;
import br.com.renan.corebanking.authorization.exception.TransactionConflictException;
import br.com.renan.corebanking.authorization.exception.UnsupportedCurrencyException;
import br.com.renan.corebanking.authorization.mapper.TransactionResponseMapper;
import br.com.renan.corebanking.authorization.repository.AccountRepository;
import br.com.renan.corebanking.authorization.repository.TransactionRepository;
import br.com.renan.corebanking.authorization.observability.TransactionAuthorizationMetrics;
import br.com.renan.corebanking.domain.shared.enums.AccountStatus;
import br.com.renan.corebanking.domain.shared.enums.FailureReason;
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
        validateCurrency(request.amount().currency());
        BigDecimal amount = validateAmount(request.amount().value());

        if (!lockProperties.isEnabled()) {
            return authorizeInTransaction(transactionId, request, amount);
        }

        String transactionKey = "lock:transaction:" + transactionId;
        String accountKey = "lock:account:" + request.accountId();

        return lockService.executeWithLock(transactionKey, lockProperties.getTransactionLockTtl(),
                lockProperties.getWaitTimeout(), lockProperties.getRetryDelay(),
                lockProperties.getMaxRetryDelay(),
                () -> lockService.executeWithLock(accountKey, lockProperties.getAccountLockTtl(),
                        lockProperties.getWaitTimeout(), lockProperties.getRetryDelay(),
                        lockProperties.getMaxRetryDelay(),
                        () -> authorizeInTransaction(transactionId, request, amount)));
    }

    private TransactionResponseDTO authorizeInTransaction(UUID transactionId,
                                                          TransactionRequestDTO request,
                                                          BigDecimal amount) {
        return transactionOperations.execute(status -> doAuthorize(transactionId, request, amount));
    }

    private TransactionResponseDTO doAuthorize(UUID transactionId, TransactionRequestDTO request, BigDecimal amount) {
        Optional<Account> accountResult = accountRepository.findByIdForUpdate(request.accountId());
        if (accountResult.isEmpty()) {
            metrics.recordAccountNotFound(request.type());
            throw new AccountNotFoundException(request.accountId());
        }
        Account account = accountResult.get();

        Optional<Transaction> existing = findExistingTransaction(transactionId);
        if (existing.isPresent()) {
            return handleReplay(transactionId, request, existing.get(), account);
        }

        if (account.getStatus() != AccountStatus.ENABLED) {
            log.info("Transaction refused (account not enabled): id={}, accountId={}, status={}",
                    transactionId, account.getId(), account.getStatus());
            Transaction refused = buildFailedTransaction(
                    transactionId, request, amount, FailureReason.ACCOUNT_DISABLED);
            Transaction saved = transactionRepository.save(refused);
            metrics.recordAuthorization(saved);
            return buildResponse(saved, account);
        }

        Transaction transaction = switch (request.type()) {
            case CREDIT -> authorizeCredit(transactionId, request, account, amount);
            case DEBIT -> authorizeDebit(transactionId, request, account, amount);
        };
        metrics.recordAuthorization(transaction);
        return buildResponse(transaction, account);
    }

    private TransactionResponseDTO handleReplay(UUID transactionId,
                                                TransactionRequestDTO request,
                                                Transaction existing,
                                                Account lockedAccount) {
        if (!isSameLogicalPayload(existing, request)) {
            metrics.recordIdempotencyConflict(request.type());
            throw new TransactionConflictException(
                    "transactionId " + transactionId + " already used with a different payload");
        }
        log.info("Idempotent replay returning stored result: id={}", transactionId);
        metrics.recordIdempotentReplay(existing);
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
        log.info("CREDIT approved: id={}, accountId={}, newBalance={}",
                transactionId, account.getId(), newBalance);
        return transactionRepository.save(buildSucceededTransaction(transactionId, request, amount));
    }

    private Transaction authorizeDebit(UUID transactionId,
                                       TransactionRequestDTO request,
                                       Account account,
                                       BigDecimal amount) {
        BigDecimal newBalance = MoneyConstants.normalize(account.getBalanceAmount().subtract(amount));
        if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
            log.info("DEBIT refused (insufficient funds): id={}, accountId={}, balance={}, amount={}",
                    transactionId, account.getId(), account.getBalanceAmount(), amount);
            Transaction refused = buildFailedTransaction(
                    transactionId, request, amount, FailureReason.INSUFFICIENT_FUNDS);
            return transactionRepository.save(refused);
        }
        account.setBalanceAmount(newBalance);
        accountRepository.save(account);
        log.info("DEBIT approved: id={}, accountId={}, newBalance={}",
                transactionId, account.getId(), newBalance);
        return transactionRepository.save(buildSucceededTransaction(transactionId, request, amount));
    }

    private Transaction buildSucceededTransaction(UUID transactionId,
                                                  TransactionRequestDTO request,
                                                  BigDecimal amount) {
        return Transaction.approved(
                transactionId, request.accountId(), request.type(),
                amount, request.amount().currency(), now());
    }

    private Transaction buildFailedTransaction(UUID transactionId,
                                               TransactionRequestDTO request,
                                               BigDecimal amount,
                                               FailureReason reason) {
        return Transaction.rejected(
                transactionId, request.accountId(), request.type(),
                amount, request.amount().currency(), reason, now());
    }

    private TransactionResponseDTO buildResponse(Transaction transaction, Account account) {
        return responseMapper.toResponse(transaction, account);
    }

    private static OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
