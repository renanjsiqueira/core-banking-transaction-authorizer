package br.com.renan.transactionauthorization.exception;

/**
 * Raised when an account-created message is malformed or carries invalid data
 * (bad UUID, unknown status, non-numeric timestamp, missing fields).
 *
 * <p>Represents a <em>permanent</em> failure: retrying the same message will
 * never succeed. The poison-message handling (DLQ / redrive policy) is addressed
 * in the resilience phase.
 */
public class InvalidAccountMessageException extends RuntimeException {

    public InvalidAccountMessageException(String message) {
        super(message);
    }
}
