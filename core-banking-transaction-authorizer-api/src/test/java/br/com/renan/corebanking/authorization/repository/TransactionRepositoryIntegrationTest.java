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
import br.com.renan.corebanking.authorization.model.Account;
import br.com.renan.corebanking.authorization.model.Transaction;
import br.com.renan.corebanking.domain.shared.enums.AccountStatus;
import br.com.renan.corebanking.domain.shared.enums.FailureReason;
import br.com.renan.corebanking.domain.shared.enums.TransactionStatus;
import br.com.renan.corebanking.domain.shared.enums.TransactionType;
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
        Account account = Account.newImportedAccount(
                UUID.randomUUID(), UUID.randomUUID(), AccountStatus.ENABLED,
                OffsetDateTime.now(ZoneOffset.UTC));
        return accountRepository.saveAndFlush(account).getId();
    }

    @Test
    void savesApprovedTransaction() {
        UUID accountId = persistAccount();
        Transaction tx = Transaction.approved(
                UUID.randomUUID(), accountId, TransactionType.CREDIT,
                new BigDecimal("97.07"), "BRL", OffsetDateTime.now(ZoneOffset.UTC));

        Transaction saved = transactionRepository.save(tx);

        Optional<Transaction> found = transactionRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getStatus()).isEqualTo(TransactionStatus.SUCCEEDED);
        assertThat(found.get().getFailureReason()).isNull();
    }

    @Test
    void savesRejectedTransaction() {
        UUID accountId = persistAccount();
        Transaction tx = Transaction.rejected(
                UUID.randomUUID(), accountId, TransactionType.DEBIT,
                new BigDecimal("10.00"), "BRL", FailureReason.INSUFFICIENT_FUNDS,
                OffsetDateTime.now(ZoneOffset.UTC));

        Transaction saved = transactionRepository.save(tx);

        Optional<Transaction> found = transactionRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getStatus()).isEqualTo(TransactionStatus.FAILED);
        assertThat(found.get().getFailureReason()).isEqualTo(FailureReason.INSUFFICIENT_FUNDS);
    }

    @Test
    void findsTransactionsByAccountOrderedByCreatedAtDesc() {
        UUID accountId = persistAccount();
        OffsetDateTime base = OffsetDateTime.now(ZoneOffset.UTC);

        Transaction oldest = approvedAt(accountId, base.minusMinutes(2));
        Transaction middle = approvedAt(accountId, base.minusMinutes(1));
        Transaction newest = approvedAt(accountId, base);
        transactionRepository.saveAll(List.of(oldest, middle, newest));

        List<Transaction> result =
                transactionRepository.findByAccountIdOrderByCreatedAtDesc(accountId);

        assertThat(result)
                .extracting(Transaction::getId)
                .containsExactly(newest.getId(), middle.getId(), oldest.getId());
    }

    @Test
    @Transactional
    void duplicateTransactionIdIsRejectedByPrimaryKey() {
        UUID accountId = persistAccount();
        UUID transactionId = UUID.randomUUID();

        Transaction first = Transaction.approved(
                transactionId, accountId, TransactionType.CREDIT,
                new BigDecimal("5.00"), "BRL", OffsetDateTime.now(ZoneOffset.UTC));
        entityManager.persist(first);
        entityManager.flush();

        Transaction duplicate = Transaction.approved(
                transactionId, accountId, TransactionType.DEBIT,
                new BigDecimal("3.00"), "BRL", OffsetDateTime.now(ZoneOffset.UTC));

        assertThatThrownBy(() -> {
            entityManager.persist(duplicate);
            entityManager.flush();
        }).isInstanceOf(PersistenceException.class);
    }

    private static Transaction approvedAt(UUID accountId, OffsetDateTime createdAt) {
        Transaction tx = Transaction.approved(
                UUID.randomUUID(), accountId, TransactionType.CREDIT,
                new BigDecimal("1.00"), "BRL", createdAt);
        tx.setCreatedAt(createdAt);
        return tx;
    }
}
