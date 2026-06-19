package br.com.renan.corebanking.authorization.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.renan.corebanking.authorization.dto.TransactionRequest;
import br.com.renan.corebanking.authorization.dto.TransactionResponse;
import br.com.renan.corebanking.authorization.entity.AccountEntity;
import br.com.renan.corebanking.authorization.entity.TransactionEntity;
import br.com.renan.corebanking.authorization.exception.AccountNotFoundException;
import br.com.renan.corebanking.authorization.exception.TransactionConflictException;
import br.com.renan.corebanking.authorization.exception.UnsupportedCurrencyException;
import br.com.renan.corebanking.authorization.mapper.TransactionResponseMapper;
import br.com.renan.corebanking.authorization.repository.AccountRepository;
import br.com.renan.corebanking.authorization.repository.TransactionRepository;
import br.com.renan.corebanking.shared.enums.AccountStatus;
import br.com.renan.corebanking.shared.enums.FailureReason;
import br.com.renan.corebanking.shared.money.MoneyConstants;

@Service
public class TransactionAuthorizationServiceImpl implements TransactionAuthorizationService {
    private static final Logger log = LoggerFactory.getLogger(TransactionAuthorizationServiceImpl.class);

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionResponseMapper responseMapper;

    public TransactionAuthorizationServiceImpl(AccountRepository accountRepository,
                                               TransactionRepository transactionRepository,
                                               TransactionResponseMapper responseMapper) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.responseMapper = responseMapper;
    }

    @Override
    @Transactional
    public TransactionResponse authorize(UUID transactionId, TransactionRequest request) {
        validateCurrency(request.amount().currency());

        Optional<TransactionEntity> existing = findExistingTransaction(transactionId);
        if (existing.isPresent()) {
            return handleReplay(transactionId, request, existing.get());
        }

        AccountEntity account = accountRepository.findByIdForUpdate(request.accountId())
                .orElseThrow(() -> new AccountNotFoundException(request.accountId()));

        BigDecimal amount = MoneyConstants.normalize(request.amount().value());

        if (account.getStatus() != AccountStatus.ENABLED) {
            log.info("Transaction refused (account not enabled): id={}, accountId={}, status={}",
                    transactionId, account.getId(), account.getStatus());
            TransactionEntity refused = buildFailedTransaction(
                    transactionId, request, amount, FailureReason.ACCOUNT_DISABLED);
            return buildResponse(transactionRepository.save(refused), account);
        }

        TransactionEntity transaction = switch (request.type()) {
            case CREDIT -> authorizeCredit(transactionId, request, account, amount);
            case DEBIT -> authorizeDebit(transactionId, request, account, amount);
        };
        return buildResponse(transaction, account);
    }

    private TransactionResponse handleReplay(UUID transactionId,
                                             TransactionRequest request,
                                             TransactionEntity existing) {
        if (!isSameLogicalPayload(existing, request)) {
            throw new TransactionConflictException(
                    "transactionId " + transactionId + " already used with a different payload");
        }
        AccountEntity account = accountRepository.findById(existing.getAccountId())
                .orElseThrow(() -> new AccountNotFoundException(existing.getAccountId()));
        log.info("Idempotent replay returning stored result: id={}", transactionId);
        return buildResponse(existing, account);
    }

    private void validateCurrency(String currency) {
        if (!MoneyConstants.DEFAULT_CURRENCY.equals(currency)) {
            throw new UnsupportedCurrencyException(currency);
        }
    }

    private Optional<TransactionEntity> findExistingTransaction(UUID transactionId) {
        return transactionRepository.findById(transactionId);
    }

    private boolean isSameLogicalPayload(TransactionEntity transaction, TransactionRequest request) {
        return transaction.getAccountId().equals(request.accountId())
                && transaction.getType() == request.type()
                && transaction.getAmount().compareTo(MoneyConstants.normalize(request.amount().value())) == 0
                && transaction.getCurrency().equals(request.amount().currency());
    }

    private TransactionEntity authorizeCredit(UUID transactionId,
                                              TransactionRequest request,
                                              AccountEntity account,
                                              BigDecimal amount) {
        BigDecimal newBalance = MoneyConstants.normalize(account.getBalanceAmount().add(amount));
        account.setBalanceAmount(newBalance);
        accountRepository.save(account);
        log.info("CREDIT approved: id={}, accountId={}, newBalance={}",
                transactionId, account.getId(), newBalance);
        return transactionRepository.save(buildSucceededTransaction(transactionId, request, amount));
    }

    private TransactionEntity authorizeDebit(UUID transactionId,
                                             TransactionRequest request,
                                             AccountEntity account,
                                             BigDecimal amount) {
        BigDecimal newBalance = MoneyConstants.normalize(account.getBalanceAmount().subtract(amount));
        if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
            log.info("DEBIT refused (insufficient funds): id={}, accountId={}, balance={}, amount={}",
                    transactionId, account.getId(), account.getBalanceAmount(), amount);
            TransactionEntity refused = buildFailedTransaction(
                    transactionId, request, amount, FailureReason.INSUFFICIENT_FUNDS);
            return transactionRepository.save(refused);
        }
        account.setBalanceAmount(newBalance);
        accountRepository.save(account);
        log.info("DEBIT approved: id={}, accountId={}, newBalance={}",
                transactionId, account.getId(), newBalance);
        return transactionRepository.save(buildSucceededTransaction(transactionId, request, amount));
    }

    private TransactionEntity buildSucceededTransaction(UUID transactionId,
                                                        TransactionRequest request,
                                                        BigDecimal amount) {
        return TransactionEntity.approved(
                transactionId, request.accountId(), request.type(),
                amount, request.amount().currency(), now());
    }

    private TransactionEntity buildFailedTransaction(UUID transactionId,
                                                     TransactionRequest request,
                                                     BigDecimal amount,
                                                     FailureReason reason) {
        return TransactionEntity.rejected(
                transactionId, request.accountId(), request.type(),
                amount, request.amount().currency(), reason, now());
    }

    private TransactionResponse buildResponse(TransactionEntity transaction, AccountEntity account) {
        return responseMapper.toResponse(transaction, account);
    }

    private static OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
