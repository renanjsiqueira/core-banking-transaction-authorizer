package br.com.renan.transactionauthorization.service;

import java.util.UUID;

import br.com.renan.transactionauthorization.dto.TransactionRequest;
import br.com.renan.transactionauthorization.dto.TransactionResponse;

/**
 * Authorizes a CREDIT/DEBIT transaction against an account, enforcing balance
 * rules, transactional consistency and idempotency by {@code transactionId}.
 */
public interface TransactionAuthorizationService {

    TransactionResponse authorize(UUID transactionId, TransactionRequest request);
}
