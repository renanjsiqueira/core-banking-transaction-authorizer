package br.com.renan.corebanking.authorization.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.renan.corebanking.authorization.entity.AccountEntity;
import br.com.renan.corebanking.shared.enums.AccountStatus;
import jakarta.persistence.LockModeType;

public interface AccountRepository extends JpaRepository<AccountEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AccountEntity a where a.id = :id")
    Optional<AccountEntity> findByIdForUpdate(@Param("id") UUID id);

    long countByStatus(AccountStatus status);
}
