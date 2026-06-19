package br.com.renan.corebanking.authorization.dto.response;

import java.math.BigDecimal;

public record AmountPayloadDTO(BigDecimal value, String currency) {
}
