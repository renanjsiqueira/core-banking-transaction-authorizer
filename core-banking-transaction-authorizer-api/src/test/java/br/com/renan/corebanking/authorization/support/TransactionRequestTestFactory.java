package br.com.renan.corebanking.authorization.support;

import java.math.BigDecimal;
import java.util.UUID;

import br.com.renan.corebanking.authorization.dto.request.AmountRequestDTO;
import br.com.renan.corebanking.authorization.dto.request.TransactionRequestDTO;
import br.com.renan.corebanking.domain.shared.enums.TransactionType;

public final class TransactionRequestTestFactory {
    private TransactionRequestTestFactory() {
    }

    public static TransactionRequestDTO of(UUID accountId, TransactionType type, String value, String currency) {
        BigDecimal amount = value == null ? null : new BigDecimal(value);
        return new TransactionRequestDTO(accountId, type, new AmountRequestDTO(amount, currency));
    }

    public static TransactionRequestDTO credit(UUID accountId, String value) {
        return of(accountId, TransactionType.CREDIT, value, "BRL");
    }

    public static TransactionRequestDTO debit(UUID accountId, String value) {
        return of(accountId, TransactionType.DEBIT, value, "BRL");
    }
}
