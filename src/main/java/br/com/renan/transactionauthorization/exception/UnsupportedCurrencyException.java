package br.com.renan.transactionauthorization.exception;

/** Raised when the transaction currency is not supported (400). */
public class UnsupportedCurrencyException extends BusinessException {

    public UnsupportedCurrencyException(String currency) {
        super("Unsupported currency: " + currency);
    }
}
