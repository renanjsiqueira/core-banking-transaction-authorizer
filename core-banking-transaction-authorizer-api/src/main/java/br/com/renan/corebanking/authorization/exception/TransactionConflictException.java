package br.com.renan.corebanking.authorization.exception;

public class TransactionConflictException extends BusinessException {
    public TransactionConflictException(String message) {
        super(message);
    }
}
