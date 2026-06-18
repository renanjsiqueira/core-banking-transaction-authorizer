package br.com.renan.transactionauthorization.enums;

/**
 * Reason a transaction authorization was refused. Stored only on FAILED
 * transactions; {@code null} for successful ones.
 */
public enum FailureReason {

    /** The target account does not exist. */
    ACCOUNT_NOT_FOUND,

    /** A DEBIT would drive the balance below zero. */
    INSUFFICIENT_FUNDS,

    /** The requested amount is missing, zero or negative. */
    INVALID_AMOUNT,

    /** The transaction currency does not match the account currency. */
    UNSUPPORTED_CURRENCY,

    /** The account is not in a state that allows transactions. */
    ACCOUNT_DISABLED,

    /** Same transaction id replayed with conflicting parameters. */
    IDEMPOTENCY_CONFLICT
}
