package br.com.renan.transactionauthorization.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import br.com.renan.transactionauthorization.enums.TransactionStatus;
import br.com.renan.transactionauthorization.enums.TransactionType;

/**
 * Transaction block of the response.
 *
 * @param id        transaction identifier
 * @param type      CREDIT or DEBIT
 * @param amount    transacted amount
 * @param status    SUCCEEDED or FAILED
 * @param timestamp authorization timestamp (ISO-8601)
 */
public record TransactionDataResponse(
        UUID id,
        TransactionType type,
        AmountResponse amount,
        TransactionStatus status,
        OffsetDateTime timestamp) {
}
