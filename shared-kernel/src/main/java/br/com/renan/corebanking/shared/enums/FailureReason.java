package br.com.renan.corebanking.shared.enums;

public enum FailureReason {
    ACCOUNT_NOT_FOUND,

    INSUFFICIENT_FUNDS,

    INVALID_AMOUNT,

    UNSUPPORTED_CURRENCY,

    ACCOUNT_DISABLED,

    IDEMPOTENCY_CONFLICT
}
