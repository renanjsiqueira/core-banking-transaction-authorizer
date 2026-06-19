package br.com.renan.transactionauthorization.exception;

import java.util.UUID;

/** Raised when authorizing a transaction against a non-existent account (404). */
public class AccountNotFoundException extends BusinessException {

    public AccountNotFoundException(UUID accountId) {
        super("Account not found: " + accountId);
    }
}
