package br.com.renan.transactionauthorization.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import br.com.renan.transactionauthorization.enums.FailureReason;
import br.com.renan.transactionauthorization.enums.TransactionStatus;
import br.com.renan.transactionauthorization.enums.TransactionType;

class TransactionEntityTest {

    @Test
    void approved_hasSucceededStatusAndNoFailureReason() {
        TransactionEntity tx = TransactionEntity.approved(
                UUID.randomUUID(), UUID.randomUUID(), TransactionType.CREDIT,
                new BigDecimal("97.07"), "BRL", OffsetDateTime.now(ZoneOffset.UTC));

        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.SUCCEEDED);
        assertThat(tx.getFailureReason()).isNull();
        assertThat(tx.getCreatedAt()).isNotNull();
    }

    @Test
    void rejected_hasFailedStatusAndFailureReason() {
        TransactionEntity tx = TransactionEntity.rejected(
                UUID.randomUUID(), UUID.randomUUID(), TransactionType.DEBIT,
                new BigDecimal("50.00"), "BRL", FailureReason.INSUFFICIENT_FUNDS,
                OffsetDateTime.now(ZoneOffset.UTC));

        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.FAILED);
        assertThat(tx.getFailureReason()).isEqualTo(FailureReason.INSUFFICIENT_FUNDS);
    }

    @Test
    void amount_isNormalizedToScaleTwo() {
        TransactionEntity tx = TransactionEntity.approved(
                UUID.randomUUID(), UUID.randomUUID(), TransactionType.CREDIT,
                new BigDecimal("100"), "BRL", OffsetDateTime.now(ZoneOffset.UTC));

        assertThat(tx.getAmount().scale()).isEqualTo(2);
        assertThat(tx.getAmount()).isEqualByComparingTo("100.00");
    }
}
