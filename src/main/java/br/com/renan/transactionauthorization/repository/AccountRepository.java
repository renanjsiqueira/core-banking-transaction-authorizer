package br.com.renan.transactionauthorization.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.renan.transactionauthorization.entity.AccountEntity;
import br.com.renan.transactionauthorization.enums.AccountStatus;
import jakarta.persistence.LockModeType;

/**
 * Persistence access for {@link AccountEntity}.
 *
 * <p>{@link #findByIdForUpdate(UUID)} acquires a {@code PESSIMISTIC_WRITE} lock
 * (SELECT ... FOR UPDATE). The critical authorization path (added later) reads
 * the account through this method so that concurrent debits on the same account
 * are serialized at the row level, guaranteeing balance consistency. It must be
 * invoked inside a transaction for the lock to be held.
 */
public interface AccountRepository extends JpaRepository<AccountEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AccountEntity a where a.id = :id")
    Optional<AccountEntity> findByIdForUpdate(@Param("id") UUID id);

    long countByStatus(AccountStatus status);
}
