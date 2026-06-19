package br.com.renan.corebanking.authorization.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
import br.com.renan.corebanking.authorization.dto.AmountRequest;
import br.com.renan.corebanking.authorization.dto.TransactionRequest;
import br.com.renan.corebanking.authorization.dto.TransactionResponse;
import br.com.renan.corebanking.authorization.entity.AccountEntity;
import br.com.renan.corebanking.authorization.repository.AccountRepository;
import br.com.renan.corebanking.shared.enums.AccountStatus;
import br.com.renan.corebanking.shared.enums.TransactionStatus;
import br.com.renan.corebanking.shared.enums.TransactionType;

class TransactionAuthorizationConcurrencyIntegrationTest extends AbstractPostgresIntegrationTest {
    @Autowired
    private TransactionAuthorizationService service;

    @Autowired
    private AccountRepository accountRepository;

    private UUID persistEnabledAccount(String balance) {
        AccountEntity account = AccountEntity.newImportedAccount(
                UUID.randomUUID(), UUID.randomUUID(), AccountStatus.ENABLED,
                OffsetDateTime.now(ZoneOffset.UTC));
        account.setBalanceAmount(new BigDecimal(balance).setScale(2));
        return accountRepository.saveAndFlush(account).getId();
    }

    private List<TransactionResponse> runConcurrently(int threads, Callable<TransactionResponse> task)
            throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<TransactionResponse>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    startGate.await();
                    return task.call();
                }));
            }
            startGate.countDown();
            List<TransactionResponse> results = new ArrayList<>();
            for (Future<TransactionResponse> future : futures) {
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
    void concurrentDebits_neverOverdrawAccount() throws InterruptedException {
        UUID accountId = persistEnabledAccount("100.00");

        List<TransactionResponse> results = runConcurrently(20, () -> service.authorize(
                UUID.randomUUID(),
                new TransactionRequest(accountId, TransactionType.DEBIT,
                        new AmountRequest(new BigDecimal("10.00"), "BRL"))));

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

        BigDecimal finalBalance = accountRepository.findById(accountId).orElseThrow().getBalanceAmount();
        assertThat(finalBalance).isEqualByComparingTo("0.00");
        assertThat(finalBalance.signum()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void concurrentCredits_sumAllAmounts() throws InterruptedException {
        UUID accountId = persistEnabledAccount("0.00");

        List<TransactionResponse> results = runConcurrently(50, () -> service.authorize(
                UUID.randomUUID(),
                new TransactionRequest(accountId, TransactionType.CREDIT,
                        new AmountRequest(new BigDecimal("1.00"), "BRL"))));

        assertThat(results).allSatisfy(r ->
                assertThat(r.transaction().status()).isEqualTo(TransactionStatus.SUCCEEDED));

        BigDecimal finalBalance = accountRepository.findById(accountId).orElseThrow().getBalanceAmount();
        assertThat(finalBalance).isEqualByComparingTo("50.00");
    }
}
