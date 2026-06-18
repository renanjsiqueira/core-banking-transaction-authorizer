package br.com.renan.transactionauthorization.enums;

/**
 * Outcome of a transaction authorization.
 *
 * <p>Maps to the challenge's API representation: {@code SUCCEEDED} (aprovada) and
 * {@code FAILED} (recusada).
 */
public enum TransactionStatus {

    /** Authorization approved; balance was updated. */
    SUCCEEDED,

    /** Authorization refused; balance was not changed. */
    FAILED
}
