package br.com.renan.corebanking.authorization.exception;

public class LockUnavailableException extends RuntimeException {
    public LockUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
