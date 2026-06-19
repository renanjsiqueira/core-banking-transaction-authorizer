package br.com.renan.corebanking.authorization.dto;

import java.util.UUID;

import br.com.renan.corebanking.shared.enums.TransactionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record TransactionRequest(

        @NotNull(message = "accountId is required")
        UUID accountId,

        @NotNull(message = "type is required and must be CREDIT or DEBIT")
        TransactionType type,

        @NotNull(message = "amount is required")
        @Valid
        AmountRequest amount) {
}
