package br.com.renan.corebanking.authorization.dto;

import java.math.BigDecimal;

public record BalanceResponse(BigDecimal amount, String currency) {
}
