package br.com.renan.corebanking.onboarding.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import br.com.renan.corebanking.onboarding.dto.AccountCreatedMessage;
import br.com.renan.corebanking.onboarding.dto.AccountPayload;
import br.com.renan.corebanking.onboarding.entity.AccountEntity;
import br.com.renan.corebanking.onboarding.exception.InvalidAccountMessageException;
import br.com.renan.corebanking.onboarding.repository.AccountRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class AccountCreatedConsumerServiceTest {
    private static final String VALID_ID = "5b19c8b6-0cc4-4c72-a989-0c2ee15fa975";
    private static final String VALID_OWNER = "315e3cfe-f4af-4cd2-b298-a449e614349a";
    private static final String VALID_CREATED_AT = "1634874339";

    private AccountRepository accountRepository;
    private AccountCreatedConsumerService service;

    @BeforeEach
    void setUp() {
        accountRepository = org.mockito.Mockito.mock(AccountRepository.class);
        service = new AccountCreatedConsumerService(accountRepository, new SimpleMeterRegistry());
    }

    private static AccountCreatedMessage message(String id, String owner, String createdAt, String status) {
        return new AccountCreatedMessage(new AccountPayload(id, owner, createdAt, status));
    }

    private AccountEntity importAndCapture(AccountCreatedMessage message) {
        when(accountRepository.existsById(any(UUID.class))).thenReturn(false);
        service.importAccount(message);
        ArgumentCaptor<AccountEntity> captor = ArgumentCaptor.forClass(AccountEntity.class);
        verify(accountRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void importsNewAccount_withZeroBalance() {
        AccountEntity saved = importAndCapture(message(VALID_ID, VALID_OWNER, VALID_CREATED_AT, "ENABLED"));

        assertThat(saved.getBalanceAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(saved.getBalanceAmount().scale()).isEqualTo(2);
    }

    @Test
    void importsNewAccount_withBrlCurrency() {
        AccountEntity saved = importAndCapture(message(VALID_ID, VALID_OWNER, VALID_CREATED_AT, "ENABLED"));

        assertThat(saved.getCurrency()).isEqualTo("BRL");
        assertThat(saved.getId()).isEqualTo(UUID.fromString(VALID_ID));
    }

    @Test
    void skipsDuplicateAccount() {
        when(accountRepository.existsById(any(UUID.class))).thenReturn(true);

        service.importAccount(message(VALID_ID, VALID_OWNER, VALID_CREATED_AT, "ENABLED"));

        verify(accountRepository, never()).save(any());
    }

    @Test
    void failsWhenAccountIdIsInvalid() {
        assertThatThrownBy(() ->
                service.importAccount(message("not-a-uuid", VALID_OWNER, VALID_CREATED_AT, "ENABLED")))
                .isInstanceOf(InvalidAccountMessageException.class);
    }

    @Test
    void failsWhenOwnerIsInvalid() {
        assertThatThrownBy(() ->
                service.importAccount(message(VALID_ID, "not-a-uuid", VALID_CREATED_AT, "ENABLED")))
                .isInstanceOf(InvalidAccountMessageException.class);
    }

    @Test
    void failsWhenStatusIsInvalid() {
        assertThatThrownBy(() ->
                service.importAccount(message(VALID_ID, VALID_OWNER, VALID_CREATED_AT, "WHATEVER")))
                .isInstanceOf(InvalidAccountMessageException.class);
    }

    @Test
    void convertsCreatedAtEpochSecondsToOffsetDateTime() {
        AccountEntity saved = importAndCapture(message(VALID_ID, VALID_OWNER, VALID_CREATED_AT, "ENABLED"));

        OffsetDateTime expected = Instant.ofEpochSecond(1634874339L).atOffset(ZoneOffset.UTC);
        assertThat(saved.getSourceCreatedAt()).isEqualTo(expected);
    }
}
