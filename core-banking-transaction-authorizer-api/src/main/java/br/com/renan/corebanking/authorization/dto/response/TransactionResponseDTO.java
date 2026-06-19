package br.com.renan.corebanking.authorization.dto.response;

public record TransactionResponseDTO(
        TransactionPayloadDTO transaction,
        AccountPayloadDTO account) {
}
