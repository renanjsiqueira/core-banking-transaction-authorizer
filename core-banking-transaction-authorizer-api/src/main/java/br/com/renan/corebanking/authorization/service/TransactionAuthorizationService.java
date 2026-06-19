package br.com.renan.corebanking.authorization.service;

import java.util.UUID;

import br.com.renan.corebanking.authorization.dto.request.TransactionRequestDTO;
import br.com.renan.corebanking.authorization.dto.response.TransactionResponseDTO;

public interface TransactionAuthorizationService {
    TransactionResponseDTO authorize(UUID transactionId, TransactionRequestDTO request);
}
