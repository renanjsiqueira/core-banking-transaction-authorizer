package br.com.renan.transactionauthorization.enums;

/**
 * Lifecycle status of a bank account, as informed by the account-opening system.
 */
public enum AccountStatus {

    /** Account is active and can take part in transactions. */
    ENABLED,

    /** Account is temporarily inactive (e.g. dormant / opted out). */
    DISABLED,

    /** Account is blocked (e.g. by compliance/fraud controls). */
    BLOCKED
}
