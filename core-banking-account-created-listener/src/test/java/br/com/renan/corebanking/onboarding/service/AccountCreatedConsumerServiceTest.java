package br.com.renan.corebanking.onboarding.service;

import static br.com.renan.corebanking.onboarding.support.AccountCreatedMessageTestFactory.VALID_CREATED_AT;
import static br.com.renan.corebanking.onboarding.support.AccountCreatedMessageTestFactory.VALID_ID;
import static br.com.renan.corebanking.onboarding.support.AccountCreatedMessageTestFactory.VALID_OWNER;
import static br.com.renan.corebanking.onboarding.support.AccountCreatedMessageTestFactory.VALID_STATUS;
import static br.com.renan.corebanking.onboarding.support.AccountCreatedMessageTestFactory.of;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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

import br.com.renan.corebanking.onboarding.dto.sqs.AccountCreatedMessageDTO;
import br.com.renan.corebanking.onboarding.model.Account;
import br.com.renan.corebanking.onboarding.exception.InvalidAccountMessageException;
import br.com.renan.corebanking.onboarding.repository.AccountRepository;
import br.com.renan.corebanking.onboarding.support.AccountCreatedMessageTestFactory;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class AccountCreatedConsumerServiceTest {
    private AccountRepository accountRepository;
    private AccountCreatedConsumerService service;

    @BeforeEach
    void setUp() {
        accountRepository = mock(AccountRepository.class);
        service = new AccountCreatedConsumerService(accountRepository, new SimpleMeterRegistry());
    }

    private Account importAndCapture(AccountCreatedMessageDTO message) {
        when(accountRepository.existsById(any(UUID.class))).thenReturn(false);
        service.importAccount(message);
        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void importsNewAccountWithZeroBalance() {
        Account saved = importAndCapture(AccountCreatedMessageTestFactory.valid());

        assertThat(saved.getBalanceAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(saved.getBalanceAmount().scale()).isEqualTo(2);
    }

    @Test
    void importsNewAccountWithBrlCurrency() {
        Account saved = importAndCapture(AccountCreatedMessageTestFactory.valid());

        assertThat(saved.getCurrency()).isEqualTo("BRL");
        assertThat(saved.getId()).isEqualTo(UUID.fromString(VALID_ID));
    }

    @Test
    void skipsDuplicateAccount() {
        when(accountRepository.existsById(any(UUID.class))).thenReturn(true);

        service.importAccount(AccountCreatedMessageTestFactory.valid());

        verify(accountRepository, never()).save(any());
    }

    @Test
    void failsWhenAccountIdIsInvalid() {
        assertThatThrownBy(() ->
                service.importAccount(of("not-a-uuid", VALID_OWNER, VALID_CREATED_AT, VALID_STATUS)))
                .isInstanceOf(InvalidAccountMessageException.class);
    }

    @Test
    void failsWhenOwnerIsInvalid() {
        assertThatThrownBy(() ->
                service.importAccount(of(VALID_ID, "not-a-uuid", VALID_CREATED_AT, VALID_STATUS)))
                .isInstanceOf(InvalidAccountMessageException.class);
    }

    @Test
    void failsWhenStatusIsInvalid() {
        assertThatThrownBy(() ->
                service.importAccount(of(VALID_ID, VALID_OWNER, VALID_CREATED_AT, "WHATEVER")))
                .isInstanceOf(InvalidAccountMessageException.class);
    }

    @Test
    void failsWhenAccountIsMissing() {
        assertThatThrownBy(() -> service.importAccount(new AccountCreatedMessageDTO(null)))
                .isInstanceOf(InvalidAccountMessageException.class);
    }

    @Test
    void failsWhenRequiredFieldIsMissing() {
        assertThatThrownBy(() ->
                service.importAccount(of(VALID_ID, VALID_OWNER, null, VALID_STATUS)))
                .isInstanceOf(InvalidAccountMessageException.class);
    }

    @Test
    void convertsCreatedAtEpochSecondsToOffsetDateTime() {
        Account saved = importAndCapture(AccountCreatedMessageTestFactory.valid());

        OffsetDateTime expected = Instant.ofEpochSecond(1634874339L).atOffset(ZoneOffset.UTC);
        assertThat(saved.getSourceCreatedAt()).isEqualTo(expected);
    }
}
