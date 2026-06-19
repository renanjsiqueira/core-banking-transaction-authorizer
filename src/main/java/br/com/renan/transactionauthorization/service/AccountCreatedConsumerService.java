package br.com.renan.transactionauthorization.service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import br.com.renan.transactionauthorization.dto.AccountCreatedMessage;
import br.com.renan.transactionauthorization.dto.AccountPayload;
import br.com.renan.transactionauthorization.entity.AccountEntity;
import br.com.renan.transactionauthorization.enums.AccountStatus;
import br.com.renan.transactionauthorization.exception.InvalidAccountMessageException;
import br.com.renan.transactionauthorization.repository.AccountRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Imports accounts from account-created events.
 *
 * <p>Idempotent by design: if the account already exists the event is treated as
 * a successful no-op. New accounts always start with a zero {@code BRL} balance.
 * Malformed payloads raise {@link InvalidAccountMessageException} (permanent
 * failure) so the caller can log and route them appropriately.
 */
@Service
public class AccountCreatedConsumerService {

    private static final Logger log = LoggerFactory.getLogger(AccountCreatedConsumerService.class);

    private final AccountRepository accountRepository;
    private final Counter importedCounter;
    private final Counter duplicatesCounter;

    public AccountCreatedConsumerService(AccountRepository accountRepository, MeterRegistry meterRegistry) {
        this.accountRepository = accountRepository;
        this.importedCounter = Counter.builder("accounts.imported.total")
                .description("Accounts imported from the account-created stream")
                .register(meterRegistry);
        this.duplicatesCounter = Counter.builder("accounts.duplicates.total")
                .description("Account-created events skipped because the account already existed")
                .register(meterRegistry);
    }

    /**
     * Imports the account described by the message, or skips it if it already
     * exists.
     *
     * @throws InvalidAccountMessageException if the payload is malformed/invalid
     */
    public void importAccount(AccountCreatedMessage message) {
        AccountPayload payload = requirePayload(message);
        log.debug("Account created event received: id={}", payload.id());

        UUID id = parseUuid(payload.id(), "account.id");
        UUID ownerId = parseUuid(payload.owner(), "account.owner");
        AccountStatus status = parseStatus(payload.status());
        OffsetDateTime sourceCreatedAt = parseEpochSeconds(payload.createdAt());

        if (accountRepository.existsById(id)) {
            duplicatesCounter.increment();
            log.info("Account already exists, skipping import: id={}", id);
            return;
        }

        AccountEntity account = AccountEntity.newImportedAccount(id, ownerId, status, sourceCreatedAt);
        accountRepository.save(account);
        importedCounter.increment();
        log.info("Account imported successfully: id={}, status={}", id, status);
    }

    private static AccountPayload requirePayload(AccountCreatedMessage message) {
        if (message == null || message.account() == null) {
            throw new InvalidAccountMessageException("missing 'account' object");
        }
        return message.account();
    }

    private static UUID parseUuid(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new InvalidAccountMessageException("missing required field: " + field);
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException e) {
            throw new InvalidAccountMessageException("invalid UUID for " + field + ": " + value);
        }
    }

    private static AccountStatus parseStatus(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidAccountMessageException("missing required field: account.status");
        }
        try {
            return AccountStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new InvalidAccountMessageException("invalid account.status: " + value);
        }
    }

    private static OffsetDateTime parseEpochSeconds(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidAccountMessageException("missing required field: account.created_at");
        }
        try {
            long epochSeconds = Long.parseLong(value.trim());
            return Instant.ofEpochSecond(epochSeconds).atOffset(ZoneOffset.UTC);
        } catch (NumberFormatException e) {
            throw new InvalidAccountMessageException("invalid account.created_at (epoch seconds): " + value);
        }
    }
}
