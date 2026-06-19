package br.com.renan.transactionauthorization.dto;

import java.math.BigDecimal;

/** Account balance in the API response (amount + ISO-4217 currency). */
public record BalanceResponse(BigDecimal amount, String currency) {
}
