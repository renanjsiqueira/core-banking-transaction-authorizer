package br.com.renan.corebanking.onboarding.exception;

public class InvalidAccountMessageException extends RuntimeException {
    public InvalidAccountMessageException(String message) {
        super(message);
    }
}
