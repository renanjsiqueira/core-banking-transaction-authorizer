package br.com.renan.transactionauthorization.service;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import br.com.renan.transactionauthorization.dto.TransactionRequest;
import br.com.renan.transactionauthorization.enums.TransactionStatus;

/**
 * Temporary placeholder implementation used to wire the endpoint contract.
 *
 * <p><strong>TODO (Phase 6):</strong> replace with the real authorization flow:
 * <ul>
 *   <li>load the account with a pessimistic lock ({@code findByIdForUpdate});</li>
 *   <li>apply CREDIT/DEBIT with non-negative balance enforcement;</li>
 *   <li>persist the ledger transaction;</li>
 *   <li>enforce idempotency by {@code transactionId}.</li>
 * </ul>
 *
 * <p>For now it echoes the request as a SUCCEEDED authorization with a synthetic
 * balance, so the API can be exercised against its real contract. No balance,
 * persistence or idempotency logic is performed here.
 */
@Service
public class TransactionAuthorizationServiceImpl implements TransactionAuthorizationService {

    private static final Logger log = LoggerFactory.getLogger(TransactionAuthorizationServiceImpl.class);

    @Override
    public AuthorizationResult authorize(UUID transactionId, TransactionRequest request) {
        // TODO Phase 6: real balance rules + pessimistic lock + idempotency.
        log.info("Authorizing transaction (placeholder): id={}, accountId={}, type={}",
                transactionId, request.accountId(), request.type());

        return new AuthorizationResult(
                transactionId,
                request.type(),
                request.amount().value(),
                request.amount().currency(),
                TransactionStatus.SUCCEEDED,
                OffsetDateTime.now(),
                request.accountId(),
                // Placeholder balance: echoes the requested amount until Phase 6.
                request.amount().value(),
                request.amount().currency());
    }
}
