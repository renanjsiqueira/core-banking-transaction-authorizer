package br.com.renan.corebanking.authorization.support;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import br.com.renan.corebanking.authorization.model.Transaction;
import br.com.renan.corebanking.domain.shared.enums.FailureReason;
import br.com.renan.corebanking.domain.shared.enums.TransactionType;

public final class TransactionTestFactory {
    private TransactionTestFactory() {
    }

    public static Transaction approved(UUID id, UUID accountId, TransactionType type, String amount) {
        return Transaction.approved(id, accountId, type, new BigDecimal(amount), "BRL",
                OffsetDateTime.now(ZoneOffset.UTC));
    }

    public static Transaction approved(UUID id, UUID accountId, TransactionType type, String amount,
                                       String resultingBalanceAmount) {
        return Transaction.approved(id, accountId, type, new BigDecimal(amount),
                new BigDecimal(resultingBalanceAmount), "BRL", OffsetDateTime.now(ZoneOffset.UTC));
    }

    public static Transaction rejected(UUID id, UUID accountId, TransactionType type, String amount,
                                             FailureReason reason) {
        return Transaction.rejected(id, accountId, type, new BigDecimal(amount), "BRL", reason,
                OffsetDateTime.now(ZoneOffset.UTC));
    }

    public static Transaction rejected(UUID id, UUID accountId, TransactionType type, String amount,
                                       String resultingBalanceAmount, FailureReason reason) {
        return Transaction.rejected(id, accountId, type, new BigDecimal(amount),
                new BigDecimal(resultingBalanceAmount), "BRL", reason, OffsetDateTime.now(ZoneOffset.UTC));
    }
}
