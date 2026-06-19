package br.com.renan.corebanking.authorization.dto;

import java.math.BigDecimal;

public record AmountResponse(BigDecimal value, String currency) {
}
