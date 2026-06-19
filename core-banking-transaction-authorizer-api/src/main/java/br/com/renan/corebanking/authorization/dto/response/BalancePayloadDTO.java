package br.com.renan.corebanking.authorization.dto.response;

import java.math.BigDecimal;

public record BalancePayloadDTO(BigDecimal amount, String currency) {
}
