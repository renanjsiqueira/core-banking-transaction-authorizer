package br.com.renan.corebanking.authorization.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.renan.corebanking.authorization.entity.TransactionEntity;

public interface TransactionRepository extends JpaRepository<TransactionEntity, UUID> {
    List<TransactionEntity> findByAccountIdOrderByCreatedAtDesc(UUID accountId);
}
