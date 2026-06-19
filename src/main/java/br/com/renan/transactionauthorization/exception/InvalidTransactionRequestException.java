package br.com.renan.transactionauthorization.exception;

/** Raised when a transaction request is semantically invalid (400). */
public class InvalidTransactionRequestException extends BusinessException {

    public InvalidTransactionRequestException(String message) {
        super(message);
    }
}
