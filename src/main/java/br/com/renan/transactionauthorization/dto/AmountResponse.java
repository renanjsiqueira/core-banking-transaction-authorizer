package br.com.renan.transactionauthorization.dto;

import java.math.BigDecimal;

/** Transaction amount in the API response (value + ISO-4217 currency). */
public record AmountResponse(BigDecimal value, String currency) {
}
