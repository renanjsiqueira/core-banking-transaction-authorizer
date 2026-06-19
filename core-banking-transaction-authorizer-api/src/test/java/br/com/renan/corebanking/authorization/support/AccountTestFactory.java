package br.com.renan.corebanking.authorization.support;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import br.com.renan.corebanking.authorization.model.Account;
import br.com.renan.corebanking.domain.shared.enums.AccountStatus;

public final class AccountTestFactory {
    private AccountTestFactory() {
    }

    public static Account account(UUID id, AccountStatus status, String balance) {
        Account account = Account.newImportedAccount(
                id, UUID.randomUUID(), status, OffsetDateTime.now(ZoneOffset.UTC));
        account.setBalanceAmount(new BigDecimal(balance).setScale(2));
        return account;
    }

    public static Account enabled(UUID id, String balance) {
        return account(id, AccountStatus.ENABLED, balance);
    }

    public static Account enabled(String balance) {
        return enabled(UUID.randomUUID(), balance);
    }
}
