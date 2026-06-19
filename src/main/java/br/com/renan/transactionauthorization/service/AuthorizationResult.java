package br.com.renan.transactionauthorization.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import br.com.renan.transactionauthorization.enums.TransactionStatus;
import br.com.renan.transactionauthorization.enums.TransactionType;

/**
 * Outcome of an authorization, returned by {@link TransactionAuthorizationService}
 * and mapped to the API response. Decouples the service from the web DTOs.
 */
public record AuthorizationResult(
        UUID transactionId,
        TransactionType type,
        BigDecimal amount,
        String currency,
        TransactionStatus status,
        OffsetDateTime timestamp,
        UUID accountId,
        BigDecimal balanceAmount,
        String balanceCurrency) {
}
