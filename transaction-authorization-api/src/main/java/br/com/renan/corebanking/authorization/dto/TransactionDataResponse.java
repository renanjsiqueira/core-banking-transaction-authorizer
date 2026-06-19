package br.com.renan.corebanking.authorization.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import br.com.renan.corebanking.shared.enums.TransactionStatus;
import br.com.renan.corebanking.shared.enums.TransactionType;

public record TransactionDataResponse(
        UUID id,
        TransactionType type,
        AmountResponse amount,
        TransactionStatus status,
        OffsetDateTime timestamp) {
}
