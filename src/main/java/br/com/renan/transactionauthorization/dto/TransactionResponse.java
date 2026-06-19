package br.com.renan.transactionauthorization.dto;

/**
 * Top-level response of the authorization endpoint, matching the challenge
 * contract: a {@code transaction} block and an {@code account} block.
 */
public record TransactionResponse(
        TransactionDataResponse transaction,
        AccountDataResponse account) {
}
