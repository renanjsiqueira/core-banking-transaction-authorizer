package br.com.renan.corebanking.authorization.dto;

public record TransactionResponse(
        TransactionDataResponse transaction,
        AccountDataResponse account) {
}
