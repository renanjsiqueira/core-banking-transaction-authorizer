package br.com.renan.transactionauthorization.dto;

import java.util.UUID;

/**
 * Account block of the response.
 *
 * @param id      account identifier
 * @param balance current account balance
 */
public record AccountDataResponse(UUID id, BalanceResponse balance) {
}
