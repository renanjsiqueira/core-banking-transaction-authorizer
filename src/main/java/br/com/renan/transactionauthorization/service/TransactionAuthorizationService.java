package br.com.renan.transactionauthorization.service;

import java.util.UUID;

import br.com.renan.transactionauthorization.dto.TransactionRequest;

/**
 * Authorizes a CREDIT/DEBIT transaction against an account.
 *
 * <p>The real balance rules (atomic debit, non-negative balance), pessimistic
 * locking and {@code transactionId} idempotency are implemented in the next
 * phase. This phase wires the contract end-to-end.
 */
public interface TransactionAuthorizationService {

    AuthorizationResult authorize(UUID transactionId, TransactionRequest request);
}
