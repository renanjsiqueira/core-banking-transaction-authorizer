package br.com.renan.corebanking.onboarding.service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import br.com.renan.corebanking.onboarding.dto.sqs.AccountCreatedMessageDTO;
import br.com.renan.corebanking.onboarding.dto.sqs.AccountCreatedPayloadDTO;
import br.com.renan.corebanking.onboarding.model.Account;
import br.com.renan.corebanking.onboarding.exception.InvalidAccountMessageException;
import br.com.renan.corebanking.onboarding.repository.AccountRepository;
import br.com.renan.corebanking.domain.shared.enums.AccountStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

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

    public void importAccount(AccountCreatedMessageDTO message) {
        AccountCreatedPayloadDTO payload = requirePayload(message);
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

        Account account = Account.newImportedAccount(id, ownerId, status, sourceCreatedAt);
        try {
            accountRepository.saveAndFlush(account);
        } catch (DataIntegrityViolationException ex) {
            duplicatesCounter.increment();
            log.info("Account already exists after concurrent import, skipping import: id={}", id);
            return;
        }
        importedCounter.increment();
        log.info("Account imported successfully: id={}, status={}", id, status);
    }

    private static AccountCreatedPayloadDTO requirePayload(AccountCreatedMessageDTO message) {
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
