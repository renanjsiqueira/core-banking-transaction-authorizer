package br.com.renan.corebanking.authorization.exception;

public class UnsupportedCurrencyException extends BusinessException {
    public UnsupportedCurrencyException(String currency) {
        super("Unsupported currency: " + currency);
    }
}
