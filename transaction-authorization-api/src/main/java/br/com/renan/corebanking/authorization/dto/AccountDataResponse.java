package br.com.renan.corebanking.authorization.dto;

import java.util.UUID;

public record AccountDataResponse(UUID id, BalanceResponse balance) {
}
