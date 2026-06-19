package br.com.renan.corebanking.authorization.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import br.com.renan.corebanking.authorization.AbstractPostgresIntegrationTest;
import br.com.renan.corebanking.authorization.entity.AccountEntity;
import br.com.renan.corebanking.authorization.entity.TransactionEntity;
import br.com.renan.corebanking.shared.enums.AccountStatus;
import br.com.renan.corebanking.shared.enums.FailureReason;
import br.com.renan.corebanking.shared.enums.TransactionStatus;
import br.com.renan.corebanking.shared.enums.TransactionType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;

class TransactionRepositoryIntegrationTest extends AbstractPostgresIntegrationTest {
    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private EntityManager entityManager;

    private UUID persistAccount() {
        AccountEntity account = AccountEntity.newImportedAccount(
                UUID.randomUUID(), UUID.randomUUID(), AccountStatus.ENABLED,
                OffsetDateTime.now(ZoneOffset.UTC));
        return accountRepository.saveAndFlush(account).getId();
    }

    @Test
    void savesApprovedTransaction() {
        UUID accountId = persistAccount();
        TransactionEntity tx = TransactionEntity.approved(
                UUID.randomUUID(), accountId, TransactionType.CREDIT,
                new BigDecimal("97.07"), "BRL", OffsetDateTime.now(ZoneOffset.UTC));

        TransactionEntity saved = transactionRepository.save(tx);

        Optional<TransactionEntity> found = transactionRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getStatus()).isEqualTo(TransactionStatus.SUCCEEDED);
        assertThat(found.get().getFailureReason()).isNull();
    }

    @Test
    void savesRejectedTransaction() {
        UUID accountId = persistAccount();
        TransactionEntity tx = TransactionEntity.rejected(
                UUID.randomUUID(), accountId, TransactionType.DEBIT,
                new BigDecimal("10.00"), "BRL", FailureReason.INSUFFICIENT_FUNDS,
                OffsetDateTime.now(ZoneOffset.UTC));

        TransactionEntity saved = transactionRepository.save(tx);

        Optional<TransactionEntity> found = transactionRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getStatus()).isEqualTo(TransactionStatus.FAILED);
        assertThat(found.get().getFailureReason()).isEqualTo(FailureReason.INSUFFICIENT_FUNDS);
    }

    @Test
    void findsTransactionsByAccountOrderedByCreatedAtDesc() {
        UUID accountId = persistAccount();
        OffsetDateTime base = OffsetDateTime.now(ZoneOffset.UTC);

        TransactionEntity oldest = approvedAt(accountId, base.minusMinutes(2));
        TransactionEntity middle = approvedAt(accountId, base.minusMinutes(1));
        TransactionEntity newest = approvedAt(accountId, base);
        transactionRepository.saveAll(List.of(oldest, middle, newest));

        List<TransactionEntity> result =
                transactionRepository.findByAccountIdOrderByCreatedAtDesc(accountId);

        assertThat(result)
                .extracting(TransactionEntity::getId)
                .containsExactly(newest.getId(), middle.getId(), oldest.getId());
    }

    @Test
    @Transactional
    void duplicateTransactionId_isRejectedByPrimaryKey() {
        UUID accountId = persistAccount();
        UUID transactionId = UUID.randomUUID();

        TransactionEntity first = TransactionEntity.approved(
                transactionId, accountId, TransactionType.CREDIT,
                new BigDecimal("5.00"), "BRL", OffsetDateTime.now(ZoneOffset.UTC));
        entityManager.persist(first);
        entityManager.flush();

        TransactionEntity duplicate = TransactionEntity.approved(
                transactionId, accountId, TransactionType.DEBIT,
                new BigDecimal("3.00"), "BRL", OffsetDateTime.now(ZoneOffset.UTC));

        assertThatThrownBy(() -> {
            entityManager.persist(duplicate);
            entityManager.flush();
        }).isInstanceOf(PersistenceException.class);
    }

    private static TransactionEntity approvedAt(UUID accountId, OffsetDateTime createdAt) {
        TransactionEntity tx = TransactionEntity.approved(
                UUID.randomUUID(), accountId, TransactionType.CREDIT,
                new BigDecimal("1.00"), "BRL", createdAt);
        tx.setCreatedAt(createdAt);
        return tx;
    }
}
