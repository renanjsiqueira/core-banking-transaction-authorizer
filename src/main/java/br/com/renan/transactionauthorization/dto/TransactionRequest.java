package br.com.renan.transactionauthorization.dto;

import java.util.UUID;

import br.com.renan.transactionauthorization.enums.TransactionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Body of {@code POST /transactions/{transactionId}}.
 *
 * @param accountId target account (required)
 * @param type      CREDIT or DEBIT (required)
 * @param amount    monetary amount (required)
 */
public record TransactionRequest(

        @NotNull(message = "accountId is required")
        UUID accountId,

        @NotNull(message = "type is required and must be CREDIT or DEBIT")
        TransactionType type,

        @NotNull(message = "amount is required")
        @Valid
        AmountRequest amount) {
}
