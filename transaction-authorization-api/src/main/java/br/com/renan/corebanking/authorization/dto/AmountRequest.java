package br.com.renan.corebanking.authorization.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record AmountRequest(

        @NotNull(message = "amount.value is required")
        @Positive(message = "amount.value must be greater than zero")
        BigDecimal value,

        @NotBlank(message = "amount.currency is required")
        @Size(min = 3, max = 3, message = "amount.currency must have 3 characters")
        @Pattern(regexp = "BRL", message = "amount.currency must be BRL")
        String currency) {
}
