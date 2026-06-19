package br.com.renan.corebanking.authorization.exception;

import java.util.UUID;

public class AccountNotFoundException extends BusinessException {
    public AccountNotFoundException(UUID accountId) {
        super("Account not found: " + accountId);
    }
}
