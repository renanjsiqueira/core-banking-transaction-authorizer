package br.com.renan.corebanking.authorization.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

import br.com.renan.corebanking.domain.shared.enums.TransactionStatus;
import br.com.renan.corebanking.domain.shared.enums.TransactionType;

public record TransactionPayloadDTO(
        UUID id,
        TransactionType type,
        AmountPayloadDTO amount,
        TransactionStatus status,
        OffsetDateTime timestamp) {
}
