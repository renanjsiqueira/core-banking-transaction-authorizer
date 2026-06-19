package br.com.renan.corebanking.authorization.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import br.com.renan.corebanking.domain.shared.enums.FailureReason;
import br.com.renan.corebanking.domain.shared.enums.TransactionStatus;
import br.com.renan.corebanking.domain.shared.enums.TransactionType;

class TransactionTest {
    @Test
    void approvedHasSucceededStatusAndNoFailureReason() {
        Transaction tx = Transaction.approved(
                UUID.randomUUID(), UUID.randomUUID(), TransactionType.CREDIT,
                new BigDecimal("97.07"), "BRL", OffsetDateTime.now(ZoneOffset.UTC));

        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.SUCCEEDED);
        assertThat(tx.getFailureReason()).isNull();
        assertThat(tx.getCreatedAt()).isNotNull();
    }

    @Test
    void rejectedHasFailedStatusAndFailureReason() {
        Transaction tx = Transaction.rejected(
                UUID.randomUUID(), UUID.randomUUID(), TransactionType.DEBIT,
                new BigDecimal("50.00"), "BRL", FailureReason.INSUFFICIENT_FUNDS,
                OffsetDateTime.now(ZoneOffset.UTC));

        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.FAILED);
        assertThat(tx.getFailureReason()).isEqualTo(FailureReason.INSUFFICIENT_FUNDS);
    }

    @Test
    void amountIsNormalizedToScaleTwo() {
        Transaction tx = Transaction.approved(
                UUID.randomUUID(), UUID.randomUUID(), TransactionType.CREDIT,
                new BigDecimal("100"), "BRL", OffsetDateTime.now(ZoneOffset.UTC));

        assertThat(tx.getAmount().scale()).isEqualTo(2);
        assertThat(tx.getAmount()).isEqualByComparingTo("100.00");
    }
}
