package br.com.renan.corebanking.authorization.service;

import java.util.UUID;

import br.com.renan.corebanking.authorization.dto.TransactionRequest;
import br.com.renan.corebanking.authorization.dto.TransactionResponse;

public interface TransactionAuthorizationService {
    TransactionResponse authorize(UUID transactionId, TransactionRequest request);
}
