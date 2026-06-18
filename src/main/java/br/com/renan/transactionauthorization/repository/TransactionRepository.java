package br.com.renan.transactionauthorization.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.renan.transactionauthorization.entity.TransactionEntity;

/**
 * Persistence access for {@link TransactionEntity}.
 *
 * <p>The primary key is the client-supplied transaction id, which will later
 * serve as the idempotency key: a replayed POST with the same id maps to the
 * same row, and the unique PK constraint prevents duplicate ledger entries.
 *
 * <p>{@code findById(UUID)} and {@code existsById(UUID)} are inherited from
 * {@link JpaRepository}.
 */
public interface TransactionRepository extends JpaRepository<TransactionEntity, UUID> {

    List<TransactionEntity> findByAccountIdOrderByCreatedAtDesc(UUID accountId);
}
