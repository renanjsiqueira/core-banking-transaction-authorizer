package br.com.renan.corebanking.authorization.dto.response;

import java.util.UUID;

public record AccountPayloadDTO(UUID id, BalancePayloadDTO balance) {
}
