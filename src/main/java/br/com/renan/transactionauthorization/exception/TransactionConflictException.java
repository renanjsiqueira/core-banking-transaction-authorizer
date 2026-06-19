package br.com.renan.transactionauthorization.exception;

/**
 * Raised when the same {@code transactionId} is replayed with conflicting
 * parameters (idempotency conflict, 409). The full idempotency logic arrives in
 * the next phase; the exception and its mapping are wired now.
 */
public class TransactionConflictException extends BusinessException {

    public TransactionConflictException(String message) {
        super(message);
    }
}
