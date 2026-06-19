package br.com.renan.transactionauthorization.exception;

/**
 * Base type for domain/business errors that map to a well-defined HTTP status
 * in {@link ApiExceptionHandler}. Distinct from infrastructure/unexpected errors
 * (which become 500).
 */
public abstract class BusinessException extends RuntimeException {

    protected BusinessException(String message) {
        super(message);
    }
}
