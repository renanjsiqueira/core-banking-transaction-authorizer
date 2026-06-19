package br.com.renan.corebanking.authorization.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeout;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import br.com.renan.corebanking.authorization.AbstractPostgresIntegrationTest;
import br.com.renan.corebanking.authorization.dto.request.TransactionRequestDTO;
import br.com.renan.corebanking.authorization.dto.response.TransactionResponseDTO;
import br.com.renan.corebanking.authorization.repository.AccountRepository;
import br.com.renan.corebanking.authorization.repository.TransactionRepository;
import br.com.renan.corebanking.authorization.support.AccountTestFactory;
import br.com.renan.corebanking.authorization.support.TransactionRequestTestFactory;
import br.com.renan.corebanking.domain.shared.enums.TransactionStatus;

class TransactionAuthorizationConcurrencyIntegrationTest extends AbstractPostgresIntegrationTest {
    @Autowired
    private TransactionAuthorizationService service;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    private UUID persistEnabledAccount(String balance) {
        return accountRepository.saveAndFlush(AccountTestFactory.enabled(balance)).getId();
    }

    private BigDecimal balanceOf(UUID accountId) {
        return accountRepository.findById(accountId).orElseThrow().getBalanceAmount();
    }

    private int persistedTransactions(UUID accountId) {
        return transactionRepository.findByAccountIdOrderByCreatedAtDesc(accountId).size();
    }

    private List<TransactionResponseDTO> runConcurrently(int threads, Callable<TransactionResponseDTO> task) {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<TransactionResponseDTO>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    startGate.await();
                    return task.call();
                }));
            }
            startGate.countDown();
            List<TransactionResponseDTO> results = new ArrayList<>();
            for (Future<TransactionResponseDTO> future : futures) {
                try {
                    results.add(future.get());
                } catch (Exception e) {
                    throw new IllegalStateException("authorization task failed", e);
                }
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void concurrentDebitsNeverOverdrawAndPersistAll() {
        UUID accountId = persistEnabledAccount("100.00");

        assertTimeout(Duration.ofSeconds(10), () -> {
            List<TransactionResponseDTO> results = runConcurrently(20, () -> service.authorize(
                    UUID.randomUUID(), TransactionRequestTestFactory.debit(accountId, "10.00")));

            AtomicInteger succeeded = new AtomicInteger();
            AtomicInteger failed = new AtomicInteger();
            results.forEach(r -> {
                if (r.transaction().status() == TransactionStatus.SUCCEEDED) {
                    succeeded.incrementAndGet();
                } else {
                    failed.incrementAndGet();
                }
            });

            assertThat(succeeded.get()).isEqualTo(10);
            assertThat(failed.get()).isEqualTo(10);
            assertThat(balanceOf(accountId)).isEqualByComparingTo("0.00");
            assertThat(balanceOf(accountId).signum()).isGreaterThanOrEqualTo(0);
            assertThat(persistedTransactions(accountId)).isEqualTo(20);
        });
    }

    @Test
    void concurrentCreditsSumAllAndPersistAll() {
        UUID accountId = persistEnabledAccount("0.00");

        assertTimeout(Duration.ofSeconds(10), () -> {
            List<TransactionResponseDTO> results = runConcurrently(50, () -> service.authorize(
                    UUID.randomUUID(), TransactionRequestTestFactory.credit(accountId, "1.00")));

            assertThat(results).allSatisfy(r ->
                    assertThat(r.transaction().status()).isEqualTo(TransactionStatus.SUCCEEDED));
            assertThat(balanceOf(accountId)).isEqualByComparingTo("50.00");
            assertThat(persistedTransactions(accountId)).isEqualTo(50);
        });
    }

    @Test
    void concurrentSameTransactionIdAppliesBalanceOnce() {
        UUID accountId = persistEnabledAccount("0.00");
        UUID transactionId = UUID.randomUUID();
        TransactionRequestDTO request = TransactionRequestTestFactory.credit(accountId, "10.00");

        assertTimeout(Duration.ofSeconds(10), () -> {
            List<TransactionResponseDTO> results =
                    runConcurrently(10, () -> service.authorize(transactionId, request));

            assertThat(results).allSatisfy(r -> {
                assertThat(r.transaction().status()).isEqualTo(TransactionStatus.SUCCEEDED);
                assertThat(r.transaction().id()).isEqualTo(transactionId);
            });
            assertThat(balanceOf(accountId)).isEqualByComparingTo("10.00");
            assertThat(persistedTransactions(accountId)).isEqualTo(1);
        });
    }
}
