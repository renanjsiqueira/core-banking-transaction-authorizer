package br.com.renan.corebanking.authorization.exception;

public class InvalidTransactionRequestException extends BusinessException {
    public InvalidTransactionRequestException(String message) {
        super(message);
    }
}
